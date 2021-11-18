/*
 * Mercurial :: Integration Tests
 * Copyright (C) 2014-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.sonarsource.it.scm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.assertj.core.data.MapEntry;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import com.sonar.orchestrator.util.ZipUtils;

public class MercurialTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @ClassRule
  public static Orchestrator orchestrator = Orchestrator.builderEnv()
    .setSonarVersion(System.getProperty("sonar.runtimeVersion", "LATEST_RELEASE[7.9]"))
    .addPlugin(FileLocation.byWildcardMavenFilename(new File("../sonar-scm-mercurial-plugin/target"), "sonar-scm-mercurial-plugin-*.jar"))
    .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", "7.3.0.27589"))
    .build();

  @Test
  public void testBlame() throws Exception {
    File projectDir = temp.newFolder();
    ZipUtils.unzip(new File("scm-repo/dummy-hg.zip"), projectDir);
    File pom = new File(projectDir, "dummy-hg/pom.xml");

    MavenBuild sonar = MavenBuild.create(pom)
      .setGoals("verify sonar:sonar")
      .setProperty("sonar.scm.disabled", "false");
    orchestrator.executeBuilds(sonar);

    assertThat(getScmData("dummy-hg:dummy:src/main/java/org/dummy/Dummy.java"))
      .contains(
        MapEntry.entry(1, new LineData("f553ba9f524c", "2012-07-18T18:26:11+0200", "david@gageot.net")),
        MapEntry.entry(2, new LineData("f553ba9f524c", "2012-07-18T18:26:11+0200", "david@gageot.net")),
        MapEntry.entry(3, new LineData("f553ba9f524c", "2012-07-18T18:26:11+0200", "david@gageot.net")));
  }

  @Test
  public void testRevisionId() throws Exception {
    File projectDir = temp.newFolder();
    ZipUtils.unzip(new File("scm-repo/dummy-hg.zip"), projectDir);
    File pom = new File(projectDir, "dummy-hg/pom.xml");
    MavenBuild sonar = MavenBuild.create(pom)
      .setGoals("verify sonar:sonar")
      .setProperty("sonar.scm.disabled", "false");
    orchestrator.executeBuilds(sonar);

    String json = orchestrator.getServer()
      .newHttpCall("api/project_analyses/search")
      .setParam("commits_by_line", "true")
      .setParam("project", "dummy-hg:dummy")
      .execute()
      .getBodyAsString();
    JsonObject obj = Json.parse(json).asObject();
    JsonArray analyses = obj.get("analyses").asArray();
    String revisionId = analyses.get(0).asObject().get("revision").asString();
    assertThat(revisionId).isEqualTo("6e55c56288ce");
  }

  private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

  private class LineData {
    private final String revision;
    private final Date date;
    private final String author;

    private LineData(String revision, String datetime, String author) throws ParseException {
      this.revision = revision;
      this.date = DATETIME_FORMAT.parse(datetime);
      this.author = author;
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(revision).append(date).append(author).toHashCode();
    }

    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, ToStringStyle.SIMPLE_STYLE);
    }
  }

  private Map<Integer, LineData> getScmData(String fileKey) throws ParseException {
    Map<Integer, LineData> result = new HashMap<>();
    String json = orchestrator.getServer()
      .newHttpCall("api/sources/scm")
      .setParam("commits_by_line", "true")
      .setParam("key", fileKey)
      .execute()
      .getBodyAsString();
    JsonValue obj = Json.parse(json);
    JsonArray array = obj.asObject().get("scm").asArray();
    for (int i = 0; i < array.size(); i++) {
      JsonArray item = array.get(i).asArray();
      // Time part was added in 5.2
      String dateOrDatetime = (String) item.get(2).asString();
      // Revision was added in 5.2
      result.put(Integer.valueOf(item.get(0).asInt()), new LineData(item.get(3).asString(), dateOrDatetime, item.get(1).asString()));
    }
    return result;
  }

}

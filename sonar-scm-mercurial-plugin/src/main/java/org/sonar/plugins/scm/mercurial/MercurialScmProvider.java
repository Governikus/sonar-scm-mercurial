/*
 * SonarQube :: Plugins :: SCM :: Mercurial
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
package org.sonar.plugins.scm.mercurial;

import java.io.File;
import java.nio.file.Path;

import javax.annotation.CheckForNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.ScmProvider;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonar.api.utils.command.StringStreamConsumer;


public class MercurialScmProvider extends ScmProvider {

  private static final Logger LOG = LoggerFactory.getLogger(MercurialScmProvider.class);

  private final MercurialBlameCommand blameCommand;
  private final CommandExecutor commandExecutor;

  public MercurialScmProvider(MercurialBlameCommand blameCommand) {
    this(CommandExecutor.create(), blameCommand);
  }

  MercurialScmProvider(CommandExecutor commandExecutor, MercurialBlameCommand blameCommand) {
    this.blameCommand = blameCommand;
    this.commandExecutor = commandExecutor;
  }

  @Override
  public String key() {
    return "hg";
  }

  @Override
  public boolean supports(File baseDir) {
    return new File(baseDir, ".hg").exists();
  }

  @Override
  public BlameCommand blameCommand() {
    return blameCommand;
  }

  @Override
  @CheckForNull
  public String revisionId(Path path)
  {
    Command cl = createCommandLine(path.toFile());

    StringStreamConsumer stdout = new StringStreamConsumer();
    StringStreamConsumer stderr = new StringStreamConsumer();
    int exitCode = execute(cl, stdout, stderr);
    if (exitCode != 0)
    {
      LOG.debug("The mercurial id command [" + cl.toString() + "] failed: " + stderr.getOutput());
      return null;
    }
    return stdout.getOutput();
  }

  private int execute(Command cl, StreamConsumer stdout, StreamConsumer stderr)
  {
    LOG.debug("Executing: " + cl);
    return commandExecutor.execute(cl, stdout, stderr, -1);
  }

  private Command createCommandLine(File workingDirectory)
  {
    Command cl = Command.create("hg");
    cl.setDirectory(workingDirectory);
    cl.addArgument("id");
    cl.addArgument("-i");
    return cl;
  }
}

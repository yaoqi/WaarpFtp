/**
   This file is part of GoldenGate Project (named also GoldenGate or GG).

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All GoldenGate Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   GoldenGate is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with GoldenGate .  If not, see <http://www.gnu.org/licenses/>.
 */
package goldengate.ftp.core.command.service;

import goldengate.common.command.exception.CommandAbstractException;
import goldengate.common.command.exception.Reply450Exception;
import goldengate.common.command.exception.Reply501Exception;
import goldengate.common.command.exception.Reply553Exception;
import goldengate.ftp.core.command.AbstractCommand;
import goldengate.ftp.core.file.FtpFile;
import goldengate.ftp.core.session.FtpSession;

/**
 * APPE command
 *
 * @author Frederic Bregier
 *
 */
public class APPE extends AbstractCommand {

    /*
     * (non-Javadoc)
     *
     * @see goldengate.ftp.core.command.AbstractCommand#exec()
     */
    public void exec() throws CommandAbstractException {
        if (!hasArg()) {
            invalidCurrentCommand();
            throw new Reply501Exception("Need a pathname as argument");
        }
        String filename = FtpSession.getBasename(getArg());
        FtpFile file = getSession().getDir().setFile(filename, true);
        if (file != null) {
            if (file.store()) {
                getSession().openDataConnection();
                getSession().getDataConn().getFtpTransferControl()
                        .setNewFtpTransfer(getCode(), file);
                return;
            }
            throw new Reply450Exception("Append operation not started");
        }
        throw new Reply553Exception("Filename not allowed");
    }

}

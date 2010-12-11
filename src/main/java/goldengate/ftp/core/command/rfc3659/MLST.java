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
package goldengate.ftp.core.command.rfc3659;

import goldengate.common.command.ReplyCode;
import goldengate.common.command.exception.CommandAbstractException;
import goldengate.ftp.core.command.AbstractCommand;

/**
 * MLST command
 *
 * @author Frederic Bregier
 *
 */
public class MLST extends AbstractCommand {

    /*
     * (non-Javadoc)
     *
     * @see goldengate.ftp.core.command.AbstractCommand#exec()
     */
    public void exec() throws CommandAbstractException {
        // First Check if any argument
        String path = null;
        if (!hasArg()) {
            path = getSession().getDir().getPwd();
        } else {
            path = getArg();
        }
        String message = getSession().getDir().fileFull(path, false);
        getSession().setReplyCode(
                ReplyCode.REPLY_250_REQUESTED_FILE_ACTION_OKAY, message);
    }

}

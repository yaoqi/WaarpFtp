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
package goldengate.ftp.core.command.rfc2428;

import goldengate.common.command.ReplyCode;
import goldengate.common.command.exception.Reply501Exception;
import goldengate.common.command.exception.Reply522Exception;
import goldengate.ftp.core.command.AbstractCommand;
import goldengate.ftp.core.utils.FtpChannelUtils;

import java.net.InetSocketAddress;

/**
 * EPRT command
 *
 * @author Frederic Bregier
 *
 */
public class EPRT extends AbstractCommand {

    /*
     * (non-Javadoc)
     *
     * @see goldengate.ftp.core.command.AbstractCommand#exec()
     */
    public void exec() throws Reply501Exception, Reply522Exception {
        // First Check if any argument
        if (!hasArg()) {
            InetSocketAddress inetSocketAddress = getSession().getDataConn()
                    .getRemoteAddress();
            getSession().getDataConn().setActive(inetSocketAddress);
            getSession().setReplyCode(
                    ReplyCode.REPLY_200_COMMAND_OKAY,
                    "EPRT command successful on (" +
                            FtpChannelUtils.get2428Address(inetSocketAddress) +
                            ")");
            return;
        }
        // Check if Inet Address is OK

        InetSocketAddress inetSocketAddress = FtpChannelUtils
                .get2428InetSocketAddress(getArg());
        if (inetSocketAddress == null) {
            // ERROR
            throw new Reply522Exception(null);
        }
        // No Check if the Client address is the same as given
        // OK now try to initialize connection (not open)
        getSession().getDataConn().setActive(inetSocketAddress);
        getSession()
                .setReplyCode(
                        ReplyCode.REPLY_200_COMMAND_OKAY,
                        "EPRT command successful on (" +
                                FtpChannelUtils
                                        .get2428Address(inetSocketAddress) +
                                ")");
    }
}

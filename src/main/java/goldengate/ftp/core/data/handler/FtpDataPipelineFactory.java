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
package goldengate.ftp.core.data.handler;

import goldengate.ftp.core.command.FtpArgumentCode.TransferMode;
import goldengate.ftp.core.command.FtpArgumentCode.TransferStructure;
import goldengate.ftp.core.command.FtpArgumentCode.TransferSubType;
import goldengate.ftp.core.command.FtpArgumentCode.TransferType;
import goldengate.ftp.core.config.FtpConfiguration;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.traffic.ChannelTrafficShapingHandler;

/**
 * Pipeline Factory for Data Network.
 *
 * @author Frederic Bregier
 *
 */
public class FtpDataPipelineFactory implements ChannelPipelineFactory {
    /**
     * Mode Codec
     */
    public static final String CODEC_MODE = "MODE";

    /**
     * Limit Codec
     */
    public static final String CODEC_LIMIT = "LIMITATION";

    /**
     * Type Codec
     */
    public static final String CODEC_TYPE = "TYPE";

    /**
     * Structure Codec
     */
    public static final String CODEC_STRUCTURE = "STRUCTURE";

    /**
     * Pipeline Executor Codec
     */
    public static final String PIPELINE_EXECUTOR = "pipelineExecutor";

    /**
     * Handler Codec
     */
    public static final String HANDLER = "handler";

    private static final FtpDataTypeCodec ftpDataTypeCodec = new FtpDataTypeCodec(
            TransferType.ASCII, TransferSubType.NONPRINT);

    private static final FtpDataStructureCodec ftpDataStructureCodec = new FtpDataStructureCodec(
            TransferStructure.FILE);

    /**
     * Business Handler Class
     */
    private final Class<? extends DataBusinessHandler> dataBusinessHandler;

    /**
     * Configuration
     */
    private final FtpConfiguration configuration;

    /**
     * Is this factory for Active mode
     */
    private final boolean isActive;

    /**
     * Constructor which Initializes some data
     *
     * @param dataBusinessHandler
     * @param configuration
     * @param active
     */
    public FtpDataPipelineFactory(
            Class<? extends DataBusinessHandler> dataBusinessHandler,
            FtpConfiguration configuration, boolean active) {
        this.dataBusinessHandler = dataBusinessHandler;
        this.configuration = configuration;
        isActive = active;
    }

    /**
     * Create the pipeline with Handler, ObjectDecoder, ObjectEncoder.
     *
     * @see org.jboss.netty.channel.ChannelPipelineFactory#getPipeline()
     */
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        // Add default codec but they will change by the channelConnected
        pipeline.addFirst(CODEC_MODE, new FtpDataModeCodec(TransferMode.STREAM,
                TransferStructure.FILE));
        pipeline
                .addLast(CODEC_LIMIT, configuration
                        .getFtpInternalConfiguration()
                        .getGlobalTrafficShapingHandler());
        ChannelTrafficShapingHandler limitChannel =
            configuration
            .getFtpInternalConfiguration()
            .newChannelTrafficShapingHandler();
        if (limitChannel != null) {
            pipeline.addLast(CODEC_LIMIT + "CHANNEL", limitChannel);
        }
        pipeline.addLast(CODEC_TYPE, ftpDataTypeCodec);
        pipeline.addLast(CODEC_STRUCTURE, ftpDataStructureCodec);
        // Threaded execution for business logic
        pipeline.addLast(PIPELINE_EXECUTOR, new ExecutionHandler(configuration
                .getFtpInternalConfiguration().getDataPipelineExecutor()));
        // and then business logic. New one on every connection
        DataBusinessHandler newbusiness = dataBusinessHandler.newInstance();
        DataNetworkHandler newNetworkHandler = new DataNetworkHandler(
                configuration, newbusiness, isActive);
        pipeline.addLast(HANDLER, newNetworkHandler);
        return pipeline;
    }
}

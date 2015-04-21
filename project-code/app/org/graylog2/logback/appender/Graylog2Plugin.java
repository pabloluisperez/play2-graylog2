/*
 * Copyright 2013 TORCH UG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.graylog2.logback.appender;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.net.HostAndPort;
import org.graylog2.gelfclient.GelfConfiguration;
import org.graylog2.gelfclient.GelfTransports;
import org.graylog2.gelfclient.transport.GelfTransport;
import org.jboss.netty.channel.*;
import org.slf4j.LoggerFactory;
import play.Application;
import play.Configuration;
import play.Plugin;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;


@SuppressWarnings("unused")
public class Graylog2Plugin extends Plugin {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Graylog2Plugin.class);
    private Long connectTimeout;
    private Boolean isTcpNoDelay;
    private Integer sendBufferSize;
    private Boolean accessLogEnabled;
    private String canonicalHostName;
    
    private final Boolean pluginEnabled;

    private ChannelFuture channelFuture;
    private GelfclientAppender gelfAppender;

    private InetSocketAddress graylog2ServerAddress;
    private Integer queueCapacity;
    private Long reconnectInterval;

    private GelfTransport transport;
    private Logger rootLogger;

    public Graylog2Plugin(Application app) {
        final Configuration config = app.configuration();
        this.pluginEnabled = config.getBoolean("graylog2.enable.plugin", false);
        if(!this.pluginEnabled){
        	return;
        }
        accessLogEnabled = config.getBoolean("graylog2.appender.send-access-log", false);
        queueCapacity = config.getInt("graylog2.appender.queue-size", 512);
        reconnectInterval = config.getMilliseconds("graylog2.appender.reconnect-interval", 500L);
        connectTimeout = config.getMilliseconds("graylog2.appender.connect-timeout", 1000L);
        isTcpNoDelay = config.getBoolean("graylog2.appender.tcp-nodelay", false);
        sendBufferSize = config.getInt("graylog2.appender.sendbuffersize", 0); // causes the socket default to be used
        try {
            canonicalHostName = config.getString("graylog2.appender.sourcehost", InetAddress.getLocalHost().getCanonicalHostName());
        } catch (UnknownHostException e) {
            canonicalHostName = "localhost";
            log.error("Unable to resolve canonical localhost name. " +
                    "Please set it manually via graylog2.appender.sourcehost or fix your lookup service, falling back to {}", canonicalHostName);
        }
        // TODO make this a list and dynamically accessible from the application
        final String hostString = config.getString("graylog2.appender.host", "127.0.0.1:12201");
        final String protocol = config.getString("graylog2.appender.protocol", "tcp");

        final HostAndPort hostAndPort = HostAndPort.fromString(hostString);

        final GelfTransports gelfTransport = GelfTransports.valueOf(protocol.toUpperCase());

        final GelfConfiguration gelfConfiguration = new GelfConfiguration(hostAndPort.getHostText(), hostAndPort.getPort())
                .transport(gelfTransport)
                .reconnectDelay(reconnectInterval.intValue())
                .queueSize(queueCapacity)
                .connectTimeout(connectTimeout.intValue())
                .tcpNoDelay(isTcpNoDelay)
                .sendBufferSize(sendBufferSize);

        this.transport = GelfTransports.create(gelfConfiguration);

        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);

        gelfAppender = new GelfclientAppender(transport, getLocalHostName());
        gelfAppender.setContext(lc);
    }

    @Override
    public void onStart() {
    	if(!this.pluginEnabled){
        	return;
        }
        gelfAppender.start();
        rootLogger.addAppender(gelfAppender);
    }

    @Override
    public void onStop() {
    	if(!this.pluginEnabled){
        	return;
        }
        rootLogger.detachAppender(gelfAppender);
        transport.stop();
    }

    public String getLocalHostName() {
        return canonicalHostName;
    }

    public GelfclientAppender getGelfAppender() {
        return gelfAppender;
    }

    public boolean isAccessLogEnabled() {
        return accessLogEnabled;
    }
}

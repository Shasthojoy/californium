/*******************************************************************************
 * Copyright (c) 2016 Bosch Software Innovations GmbH and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Bosch Software Innovations GmbH - initial implementation. 
 *                                      add support for correlation context
 *    Achim Kraus (Bosch Software Innovations GmbH) - add principal and 
 *                                                    add TLS information to
 *                                                    correlation context
 ******************************************************************************/
package org.eclipse.californium.elements.tcp;

import java.security.Principal;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.eclipse.californium.elements.CorrelationContext;
import org.eclipse.californium.elements.TcpCorrelationContext;
import org.eclipse.californium.elements.TlsCorrelationContext;
import org.eclipse.californium.elements.util.StringUtil;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;

/**
 * Utils for building for TCP/TLS correlation context and principal from
 * channel.
 */
public class NettyContextUtils {

	private static final Logger LOGGER = Logger.getLogger(NettyContextUtils.class.getName());
	private static final Level LEVEL = Level.FINER;

	/**
	 * Get principal related to the provided channel.
	 * 
	 * @param channel channel of principal
	 * @return principal, or null, if not available
	 */
	public static Principal getPrincipal(Channel channel) {
		SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
		if (null != sslHandler) {
			SSLEngine sslEngine = sslHandler.engine();
			SSLSession sslSession = sslEngine.getSession();
			if (null != sslSession) {
				try {
					Principal principal = sslSession.getPeerPrincipal();
					if (null == principal) {
						LOGGER.log(Level.WARNING, "Principal missing");
					} else {
						LOGGER.log(LEVEL, "Principal {0}", principal.getName());
					}
					return principal;
				} catch (SSLPeerUnverifiedException e) {
					LOGGER.log(Level.WARNING, "Principal {0}", e.getMessage());
					/* ignore it */
				}
			}
		}
		return null;
	}

	/**
	 * Build correlation context related to the provided channel.
	 * 
	 * @param channel channel of correlation context
	 * @return correlation context, or null, if yet not available.
	 * @throws IllegalStateException, if TLS handshake is ongoing
	 */
	public static CorrelationContext buildCorrelationContext(Channel channel) {
		String id = channel.id().asShortText();
		SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
		if (null != sslHandler) {
			SSLEngine sslEngine = sslHandler.engine();
			SSLSession sslSession = sslEngine.getSession();
			if (null != sslSession) {
				byte[] sessionId = sslSession.getId();
				if (null != sessionId && 0 < sessionId.length) {
					String sslId = StringUtil.byteArray2HexString(sessionId, 0);
					String cipherSuite = sslSession.getCipherSuite();
					LOGGER.log(LEVEL, "TLS({0},{1},{2})", new Object[] { StringUtil.trunc(sslId, 14), cipherSuite });
					return new TlsCorrelationContext(id, sslId, cipherSuite);
				}
			}
			// TLS handshake not finished
			throw new IllegalStateException("TLS handshake " + id + " not ready!");
		}
		LOGGER.log(LEVEL, "TCP({0})", id);
		return new TcpCorrelationContext(id);
	}
}

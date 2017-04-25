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
 *    Achim Kraus (Bosch Software Innovations GmbH) - initial implementation
 *                                                    stuff copied from TcpConnectorTest
 *    Achim Kraus (Bosch Software Innovations GmbH) - adjust creation of oubound message
 *                                                    with null correlation context.
 *    Achim Kraus (Bosch Software Innovations GmbH) - add sending correlation context.
 *    Achim Kraus (Bosch Software Innovations GmbH) - replace "any/0.0.0.0" with 
 *                                                    "localhost/127.0.0.1" in destination.
 *    Achim Kraus (Bosch Software Innovations GmbH) - add create server address
 *                                                    using LoopbackAddress.
 *    Achim Kraus (Bosch Software Innovations GmbH) - add TLS/x509 support
 *    Achim Kraus (Bosch Software Innovations GmbH) - add self-signed credentials
 ******************************************************************************/
package org.eclipse.californium.elements.tcp;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Random;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.eclipse.californium.elements.CorrelationContext;
import org.eclipse.californium.elements.MessageCallback;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.util.SslContextLoggingUtil;
import org.eclipse.californium.elements.util.SslContextUtil;
import org.eclipse.californium.elements.util.SslContextUtil.Credentials;

/**
 * Utils for TCP/TLS based connector tests.
 */
public class ConnectorTestUtil {

	public static final int NUMBER_OF_CONNECTIONS = 50;
	public static final int NUMBER_OF_THREADS = 1;
	public static final int CONNECTION_TIMEOUT_IN_MS = 100;
	public static final int IDLE_TIMEOUT_IN_S = 100;
	public static final int IDLE_TIMEOUT_RECONNECT_IN_S = 2;
	public static final int CONTEXT_TIMEOUT_IN_MS = 1000;

	public static final char[] KEY_STORE_PASSWORD = "endPass".toCharArray();
	public static final String KEY_STORE_LOCATION = "certs/keyStore.jks";
	public static final char[] TRUST_STORE_PASSWORD = "rootPass".toCharArray();
	public static final String TRUST_STORE_LOCATION = "certs/trustStore.jks";
	public static final String NO_TRUST_STORE_LOCATION = "certs/noTrustStore.jks";
	public static final char[] SELF_SIGNED_KEY_STORE_PASSWORD = "selfPass".toCharArray();
	public static final String SELF_SIGNED_KEY_STORE_LOCATION = "certs/selfSignedKeyStore.jks";

	public static final String SELF_SIGNED_SERVER_NAME = "cf-self-signed-server";
	public static final String SELF_SIGNED_CLIENT_NAME = "cf-self-signed-client-1";
	public static final String SELF_SIGNED_NO_TRUST_NAME = "cf-self-signed-client-2";
	public static final String SELF_SIGNED_CLIENT_NAME_PATTERN = "cf-self-signed-client-.*";

	public static final String SERVER_NAME = "server";
	public static final String CLIENT_NAME = "client";

	private static final Random random = new Random(0);

	public static SSLContext serverSslContext;
	public static SSLContext clientSslContext;
	public static Principal serverSubjectDN;
	public static Principal clientSubjectDN;

	public static void initializeSsl() throws Exception {
		KeyManager[] clientKeys = SslContextUtil.loadKeyManager(SslContextUtil.CLASSPATH_PROTOCOL + KEY_STORE_LOCATION,
				CLIENT_NAME, KEY_STORE_PASSWORD, KEY_STORE_PASSWORD);
		KeyManager[] serverKeys = SslContextUtil.loadKeyManager(SslContextUtil.CLASSPATH_PROTOCOL + KEY_STORE_LOCATION,
				SERVER_NAME, KEY_STORE_PASSWORD, KEY_STORE_PASSWORD);
		TrustManager[] trusts = SslContextUtil.loadTrustManager(SslContextUtil.CLASSPATH_PROTOCOL
				+ TRUST_STORE_LOCATION, null, TRUST_STORE_PASSWORD);

		clientSubjectDN = getSubjectDN(clientKeys, CLIENT_NAME);
		serverSubjectDN = getSubjectDN(serverKeys, SERVER_NAME);
		log("client " + clientSubjectDN, clientKeys);
		log("server " + serverSubjectDN, serverKeys);
		log("trusts", trusts, true);

		clientKeys = SslContextLoggingUtil.logging(clientKeys, "client");
		serverKeys = SslContextLoggingUtil.logging(serverKeys, "server");
		trusts = SslContextLoggingUtil.logging(trusts, "trust");

		// Initialize the SSLContext to work with our key managers.
		serverSslContext = SSLContext.getInstance("TLS");
		serverSslContext.init(serverKeys, trusts, null);

		clientSslContext = SSLContext.getInstance("TLS");
		clientSslContext.init(clientKeys, trusts, null);
	}

	/**
	 * Initialize a SSL context.
	 * 
	 * @param aliasPrivateKey alias for private key. If <code>null</code>,
	 *            replaced by aliasChain.
	 * @param aliasChain alias for certificate chain.
	 * @param aliasTrustsPattern alias pattern for trusts.
	 * @return ssl context.
	 * @throws Exception if an error occurred
	 */
	public static SSLTestContext initializeContext(String aliasPrivateKey, String aliasChain, String aliasTrustsPattern)
			throws Exception {
		if (null == aliasPrivateKey) {
			aliasPrivateKey = aliasChain;
		}
		Credentials credentials = SslContextUtil.loadCredentials(
				SslContextUtil.CLASSPATH_PROTOCOL + KEY_STORE_LOCATION, aliasPrivateKey, KEY_STORE_PASSWORD,
				KEY_STORE_PASSWORD);
		X509Certificate[] chain = SslContextUtil.loadCertificateChain(SslContextUtil.CLASSPATH_PROTOCOL
				+ KEY_STORE_LOCATION, aliasChain, KEY_STORE_PASSWORD);

		KeyManager[] keys = SslContextUtil.createKeyManager(aliasChain, credentials.getPrivateKey(), chain);

		TrustManager[] trusts = SslContextUtil.loadTrustManager(SslContextUtil.CLASSPATH_PROTOCOL
				+ TRUST_STORE_LOCATION, aliasTrustsPattern, TRUST_STORE_PASSWORD);

		Principal subjectDN = getSubjectDN(keys, aliasChain);
		log("keys ", keys);
		log("trusts", trusts, true);

		keys = SslContextLoggingUtil.logging(keys, aliasChain);
		trusts = SslContextLoggingUtil.logging(trusts, aliasChain);

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(keys, trusts, null);
		return new SSLTestContext(context, subjectDN);
	}

	/**
	 * Initialize a SSL context using trusts from "no trust store."
	 * 
	 * @param aliasPrivateKey alias for private key. If <code>null</code>,
	 *            replaced by aliasChain.
	 * @param aliasChain alias for certificate chain.
	 * @param aliasTrustsPattern alias pattern for trusts.
	 * @return ssl context.
	 * @throws Exception if an error occurred
	 */
	public static SSLTestContext initializeNoTrustContext(String aliasPrivateKey, String aliasChain,
			String aliasTrustsPattern) throws Exception {
		if (null == aliasPrivateKey) {
			aliasPrivateKey = aliasChain;
		}
		Credentials credentials = SslContextUtil.loadCredentials(
				SslContextUtil.CLASSPATH_PROTOCOL + KEY_STORE_LOCATION, aliasPrivateKey, KEY_STORE_PASSWORD,
				KEY_STORE_PASSWORD);
		X509Certificate[] chain = SslContextUtil.loadCertificateChain(SslContextUtil.CLASSPATH_PROTOCOL
				+ KEY_STORE_LOCATION, aliasChain, KEY_STORE_PASSWORD);

		KeyManager[] keys = SslContextUtil.createKeyManager(aliasChain, credentials.getPrivateKey(), chain);

		TrustManager[] trusts = SslContextUtil.loadTrustManager(SslContextUtil.CLASSPATH_PROTOCOL
				+ NO_TRUST_STORE_LOCATION, aliasTrustsPattern, TRUST_STORE_PASSWORD);

		Principal subjectDN = getSubjectDN(keys, aliasChain);
		log("keys ", keys);
		log("trusts", trusts, true);

		keys = SslContextLoggingUtil.logging(keys, aliasChain);
		trusts = SslContextLoggingUtil.logging(trusts, aliasChain + "-no-trust");

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(keys, trusts, null);
		return new SSLTestContext(context, subjectDN);
	}

	/**
	 * Initialize a SSL context using the "self signed key store."
	 * 
	 * @param aliasPrivateKey alias for private key. If <code>null</code>,
	 *            replaced by aliasChain.
	 * @param aliasChain alias for certificate chain.
	 * @param aliasTrustsPattern alias pattern for trusts.
	 * @return ssl context.
	 * @throws Exception if an error occurred
	 */
	public static SSLTestContext initializeSelfSignedContext(String aliasPrivateKey, String aliasChain,
			String aliasTrustsPattern) throws Exception {
		if (null == aliasPrivateKey) {
			aliasPrivateKey = aliasChain;
		}
		Credentials credentials = SslContextUtil.loadCredentials(SslContextUtil.CLASSPATH_PROTOCOL
				+ SELF_SIGNED_KEY_STORE_LOCATION, aliasPrivateKey, SELF_SIGNED_KEY_STORE_PASSWORD,
				SELF_SIGNED_KEY_STORE_PASSWORD);
		X509Certificate[] chain = SslContextUtil.loadCertificateChain(SslContextUtil.CLASSPATH_PROTOCOL
				+ SELF_SIGNED_KEY_STORE_LOCATION, aliasChain, SELF_SIGNED_KEY_STORE_PASSWORD);

		KeyManager[] keys = SslContextUtil.createKeyManager(aliasChain, credentials.getPrivateKey(), chain);

		TrustManager[] trusts = SslContextUtil.loadTrustManager(SslContextUtil.CLASSPATH_PROTOCOL
				+ SELF_SIGNED_KEY_STORE_LOCATION, aliasTrustsPattern, SELF_SIGNED_KEY_STORE_PASSWORD);

		Principal subjectDN = getSubjectDN(keys, aliasChain);
		ConnectorTestUtil.log("keys ", keys);
		ConnectorTestUtil.log("trusts", trusts, true);

		keys = SslContextLoggingUtil.logging(keys, aliasChain);
		trusts = SslContextLoggingUtil.logging(trusts, aliasChain);

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(keys, trusts, null);
		return new SSLTestContext(context, subjectDN);
	}

	public static Principal getSubjectDN(KeyManager[] manager, String alias) {
		if (null != manager && 0 < manager.length) {
			if (manager[0] instanceof X509ExtendedKeyManager) {
				X509ExtendedKeyManager extendedManager = (X509ExtendedKeyManager) manager[0];
				X509Certificate[] chain = extendedManager.getCertificateChain(alias);
				if (null != chain && 0 < chain.length) {
					return chain[0].getSubjectX500Principal();
				}
			}
		}
		return null;
	}

	public static void log(String name, KeyManager[] managers) {
		if (null == managers) {
			System.out.println(name + ": null");
			return;
		}
		System.out.println(name + ": " + managers.length);
		for (KeyManager manager : managers) {
			System.out.println("   " + manager);
		}
	}

	public static void log(String name, TrustManager[] managers, boolean logCertificate) {
		if (null == managers) {
			System.out.println(name + ": null");
			return;
		}
		System.out.println(name + ": " + managers.length);
		for (TrustManager manager : managers) {
			System.out.println("   " + manager);
			if (logCertificate && manager instanceof X509ExtendedTrustManager) {
				X509ExtendedTrustManager extendedManager = (X509ExtendedTrustManager) manager;
				X509Certificate[] issuers = extendedManager.getAcceptedIssuers();
				if (null != issuers) {
					for (X509Certificate issuer : issuers) {
						System.out.println("      " + issuer.getSubjectX500Principal().getName());
					}
				}
			}
		}
	}

	public static InetSocketAddress createServerAddress(int port) {
		return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
	}

	public static InetSocketAddress getDestination(InetSocketAddress server) {
		if (server.getAddress().isAnyLocalAddress()) {
			// for destination replace any by localhost
			server = new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getPort());
		}
		return server;
	}

	public static RawData createMessage(InetSocketAddress address, int messageSize, CorrelationContext contextToSent,
			MessageCallback callback) throws Exception {
		byte[] data = new byte[messageSize];
		random.nextBytes(data);

		try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			if (messageSize < 13) {
				stream.write(messageSize << 4);
			} else if (messageSize < (1 << 8) + 13) {
				stream.write(13 << 4);
				stream.write(messageSize - 13);
			} else if (messageSize < (1 << 16) + 269) {
				stream.write(14 << 4);

				ByteBuffer buffer = ByteBuffer.allocate(2);
				buffer.putShort((short) (messageSize - 269));
				stream.write(buffer.array());
			} else {
				stream.write(15 << 4);

				ByteBuffer buffer = ByteBuffer.allocate(4);
				buffer.putInt(messageSize - 65805);
				stream.write(buffer.array());
			}

			stream.write(1); // GET
			stream.write(data);
			stream.flush();

			return RawData.outbound(stream.toByteArray(), getDestination(address), contextToSent, callback, false);
		}
	}

	public static class SSLTestContext {

		public final SSLContext context;
		public final Principal subjectDN;

		public SSLTestContext(final SSLContext context, final Principal subjectDN) {
			this.context = context;
			this.subjectDN = subjectDN;
		}
	}
}

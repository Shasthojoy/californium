/*******************************************************************************
 * Copyright (c) 2017 Bosch Software Innovations GmbH and others.
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
 *    Bosch Software Innovations GmbH - initial implementation
 ******************************************************************************/
package org.eclipse.californium.elements;

/**
 * TLS correlation context matcher.
 */
public class TlsCorrelationContextMatcher extends KeySetCorrelationContextMatcher {

	private static final String KEYS[] = { TcpCorrelationContext.KEY_CONNECTION_ID, CorrelationContext.KEY_SESSION_ID,
			TlsCorrelationContext.KEY_CIPHER };

	public TlsCorrelationContextMatcher() {
		super("tls correlation", KEYS);
	}
}

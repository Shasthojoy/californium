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
 *    Achim Kraus (Bosch Software Innovations GmbH) - add type check to getter
 ******************************************************************************/
package org.eclipse.californium.elements.tcp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.eclipse.californium.elements.CorrelationContext;
import org.eclipse.californium.elements.MessageCallback;

/**
 * A simple message callback to access the correlation context when sending a
 * message.
 */
public class SimpleMessageCallback implements MessageCallback {

	/**
	 * Correlation context of sent message.
	 */
	private CorrelationContext context;

	@Override
	public synchronized void onContextEstablished(CorrelationContext context) {
		this.context = context;
		notifyAll();
	}

	/**
	 * Get typed correlation context of sent message.
	 * 
	 * Assert, that correlation context is of provided type.
	 * 
	 * @param clz class to check the correlation context.
	 * @return correlation context of sent message, or null, if not jet sent or
	 *         no correlation context is available.
	 * @see #getCorrelationContext(long)
	 */
	@SuppressWarnings("unchecked")
	public <T extends CorrelationContext> T getCorrelationContext(Class<T> clz) {
		CorrelationContext context;
		synchronized (this) {
			context = this.context;
		}
		assertThat(context, is(instanceOf(clz)));
		return (T) context;
	}

	/**
	 * Get correlation context of sent message waiting with timeout.
	 * 
	 * Assert, that correlation context is of provided type.
	 * 
	 * @param clz class to check the correlation context.
	 * @return correlation context of sent message, or null, if not sent within
	 *         provided timeout or no correlation context is available.
	 * @see #getCorrelationContext(long)
	 */
	@SuppressWarnings("unchecked")
	public synchronized <T extends CorrelationContext> T getCorrelationContext(Class<T> clz, long timeout)
			throws InterruptedException {
		if (null == context) {
			wait(timeout);
		}
		assertThat(context, is(instanceOf(clz)));
		return (T) context;
	}
}

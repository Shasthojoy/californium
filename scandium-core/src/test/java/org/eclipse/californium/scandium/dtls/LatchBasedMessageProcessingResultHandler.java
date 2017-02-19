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
 *    Bosch Software Innovations - initial creation
 ******************************************************************************/
package org.eclipse.californium.scandium.dtls;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.scandium.dtls.Handshaker.MessageProcessingResultHandler;


/**
 * A result handler supporting tests that need to react upon the outcome of
 * the processing of a particular handshake message.
 *
 */
public class LatchBasedMessageProcessingResultHandler implements MessageProcessingResultHandler {

	private CountDownLatch errorLatch = new CountDownLatch(1);
	private CountDownLatch messageLatch = new CountDownLatch(1);
	private DTLSMessage lastMessage;
	private RecordProcessingException lastError;

	/**
	 * 
	 */
	public LatchBasedMessageProcessingResultHandler() {
	}

	@Override
	public void onRecordProcessingError(RecordProcessingException e) {
		errorLatch.countDown();
		this.lastError = e;
	}

	@Override
	public void onMessageProcessed(DTLSMessage message) {
		messageLatch.countDown();
		this.lastMessage = message;
	}

	public DTLSMessage getLastMessage(final long maxMillisToWait) {
		try {
			messageLatch.await(maxMillisToWait, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return lastMessage;
	}

	public RecordProcessingException getLastError(final long maxMillisToWait) {
		try {
			errorLatch.await(maxMillisToWait, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return lastError;
	}

	public void setExpectedMessages(int count) {
		this.messageLatch = new CountDownLatch(count);
		lastMessage = null;
	}
}

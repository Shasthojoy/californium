package org.eclipse.californium.scandium.dtls;

import java.net.InetSocketAddress;

import org.eclipse.californium.scandium.dtls.AlertMessage.AlertDescription;

public class RecordDecryptionException extends RecordParsingException {

	private static final long serialVersionUID = 1L;

	public RecordDecryptionException(Record record, String message) {
		this(record, message, null);
	}

	public RecordDecryptionException(Record record, String message, Throwable cause) {
		this(record.getType(), record.getPeerAddress(), message, cause);
	}

	public RecordDecryptionException(ContentType recordType, InetSocketAddress peerAddress, String message) {
		this(recordType, peerAddress, message, null);
	}

	public RecordDecryptionException(ContentType recordType, InetSocketAddress peerAddress, String message, Throwable cause) {
		super(recordType, peerAddress, message, AlertDescription.DECRYPT_ERROR, cause);
	}

}

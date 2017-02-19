package org.eclipse.californium.scandium.dtls;

import java.net.InetSocketAddress;

import org.eclipse.californium.scandium.dtls.AlertMessage.AlertDescription;
import org.eclipse.californium.scandium.dtls.AlertMessage.AlertLevel;

public class RecordParsingException extends RecordProcessingException {

	private static final long serialVersionUID = 1L;

	public RecordParsingException(Record record, String message) {
		this(record, message, (AlertDescription) null);
	}

	public RecordParsingException(Record record, String message, AlertDescription description) {
		this(record, message, description, null);
	}

	public RecordParsingException(Record record, String message, Throwable cause) {
		this(record.getType(), record.getPeerAddress(), message, cause);
	}

	public RecordParsingException(Record record, String message, AlertDescription description, Throwable cause) {
		this(record.getType(), record.getPeerAddress(), message, description, cause);
	}

	public RecordParsingException(ContentType recordType, InetSocketAddress peerAddress, String message) {
		this(recordType, peerAddress, message, (Throwable) null);
	}

	public RecordParsingException(ContentType recordType, InetSocketAddress peerAddress, String message, AlertDescription description) {
		this(recordType, peerAddress, message, description, null);
	}

	public RecordParsingException(ContentType recordType, InetSocketAddress peerAddress, String message,
			Throwable cause) {
		this(recordType, peerAddress, message, AlertDescription.DECODE_ERROR, cause);
	}

	public RecordParsingException(ContentType recordType, InetSocketAddress peerAddress, String message,
			AlertDescription description, Throwable cause) {
		super(recordType, peerAddress, message, AlertLevel.FATAL, description, cause);
	}
}

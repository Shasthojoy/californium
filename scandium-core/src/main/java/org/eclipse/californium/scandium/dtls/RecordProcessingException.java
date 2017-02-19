package org.eclipse.californium.scandium.dtls;

import java.net.InetSocketAddress;

import org.eclipse.californium.scandium.dtls.AlertMessage.AlertDescription;
import org.eclipse.californium.scandium.dtls.AlertMessage.AlertLevel;

/**
 * Indicates a problem with processing a DTLS record.
 */
public class RecordProcessingException extends Exception {

	private static final long serialVersionUID = 1L;
	private final InetSocketAddress peerAddress;
	private final ContentType recordType;
	private final AlertLevel severity;
	private final AlertDescription description;

	public RecordProcessingException(Record record, String message, AlertLevel severity, AlertDescription description) {
		this(record, message, severity, description, null);
	}

	public RecordProcessingException(Record record, String message, AlertLevel severity, AlertDescription description, Throwable cause) {
		this(record.getType(), record.getPeerAddress(), message, severity, description, cause);
	}

	public RecordProcessingException(ContentType recordType, InetSocketAddress peerAddress, String message, AlertLevel severity, AlertDescription description) {
		this(recordType, peerAddress, message, severity, description, null);
	}

	public RecordProcessingException(ContentType recordType, InetSocketAddress peerAddress, String message, AlertLevel severity, AlertDescription description, Throwable cause) {
		super(message, cause);
		if (recordType == null) {
			throw new NullPointerException("Record type must not be null");
		} else if (peerAddress == null) {
			throw new NullPointerException("Peer address must not be null");
		} else if (severity == null) {
			throw new NullPointerException("Severity must not be null");
		} else if (description == null) {
			throw new NullPointerException("Description must not be null");
		} else {
			this.recordType = recordType;
			this.peerAddress = peerAddress;
			this.severity = severity;
			this.description = description;
		}
	}

	public final InetSocketAddress getPeerAddress() {
		return peerAddress;
	}

	public final ContentType getRecordType() {
		return recordType;
	}

	public final AlertLevel getSeverity() {
		return severity;
	}

	public final AlertDescription getDescription() {
		return description;
	}

	public final AlertMessage getAlert() {
		return new AlertMessage(severity, description, peerAddress);
	}
}

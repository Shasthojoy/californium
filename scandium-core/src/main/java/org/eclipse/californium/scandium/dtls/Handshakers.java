package org.eclipse.californium.scandium.dtls;

import java.util.concurrent.Executor;

import org.eclipse.californium.scandium.config.DtlsConnectorConfig;

/**
 * A factory for creating client and server side {@code Handshaker}s which
 * can be used to negotiate a new session or resuming an existing session.
 *
 */
public final class Handshakers {

	private Handshakers() {
		// prevent instantiation
	}

	/**
	 * Creates a new handshaker for negotiating a DTLS session with a server.
	 * 
	 * @param session
	 *            the session to negotiate with the server.
	 * @param recordLayer
	 *            the object to use for sending flights to the peer.
	 * @param sessionListener
	 *            the listener to notify about the session's life-cycle events.
	 * @param config
	 *            the DTLS configuration.
	 * @param maxTransmissionUnit
	 *            the MTU value reported by the network interface the record layer is bound to.
	 * @param taskExecutor
	 *            The executor to use for processing handshake messages.
	 * @throws IllegalStateException
	 *            if the message digest required for computing the FINISHED message hash cannot be instantiated.
	 * @throws NullPointerException
	 *            if any of session, record layer, config or task executor is {@code null}.
	 */
	public static ClientHandshaker client(
			final DTLSSession session,
			final RecordLayer recordLayer,
			final SessionListener sessionListener,
			final DtlsConnectorConfig config,
			final int maxTransmissionUnit,
			final Executor taskExecutor) {

		return new ClientHandshaker(session, recordLayer, sessionListener, config, maxTransmissionUnit, taskExecutor);
	}

	/**
	 * Creates a new handshaker for resuming an existing session with a server.
	 * 
	 * @param session
	 *            the session to resume.
	 * @param recordLayer
	 *            the object to use for sending flights to the peer.
	 * @param sessionListener
	 *            the listener to notify about the session's life-cycle events.
	 * @param config
	 *            the DTLS configuration parameters to use for the handshake.
	 * @param maxTransmissionUnit
	 *            the MTU value reported by the network interface the record layer is bound to.
	 * @param taskExecutor
	 *            The executor to use for processing handshake messages.
	 * @throws IllegalArgumentException
	 *            if the given session does not contain an identifier.
	 * @throws IllegalStateException
	 *            if the message digest required for computing the FINISHED message hash cannot be instantiated.
	 * @throws NullPointerException
	 *            if any of session, record layer, config or task executor is {@code null}.
	 */
	public static ResumingClientHandshaker resumingClient(
			final DTLSSession session,
			final RecordLayer recordLayer,
			final SessionListener sessionListener,
			final DtlsConnectorConfig config,
			final int maxTransmissionUnit,
			final Executor taskExecutor) {

		return new ResumingClientHandshaker(session, recordLayer, sessionListener, config, maxTransmissionUnit, taskExecutor);
	}

	/**
	 * Creates a handshaker for negotiating a DTLS session with a client
	 * following the full DTLS handshake protocol. 
	 * 
	 * @param session
	 *            the session to negotiate with the client.
	 * @param recordLayer
	 *            the object to use for sending flights to the peer.
	 * @param sessionListener
	 *            the listener to notify about the session's life-cycle events.
	 * @param config
	 *            the DTLS configuration.
	 * @param maxTransmissionUnit
	 *            the MTU value reported by the network interface the record layer is bound to.
	 * @param taskExecutor
	 *            The executor to use for processing handshake messages.
	 * @throws HandshakeException if the handshaker cannot be initialized
	 * @throws NullPointerException
	 *            if session, record layer or task executor is <code>null</code>.
	 */
	public static ServerHandshaker server(
			final DTLSSession session,
			final RecordLayer recordLayer,
			final SessionListener sessionListener,
			final DtlsConnectorConfig config,
			final int maxTransmissionUnit,
			final Executor taskExecutor) throws HandshakeException {

		return new ServerHandshaker(session, recordLayer, sessionListener, config, maxTransmissionUnit, taskExecutor);
	}

	/**
	 * Creates a handshaker for negotiating a DTLS session with a client
	 * following the full DTLS handshake protocol. 
	 * 
	 * @param initialMessageSequenceNo
	 *            the initial message sequence number to expect from the peer
	 *            (this parameter can be used to initialize the <em>receive_next_seq</em>
	 *            counter to another value than 0, e.g. if one or more cookie exchange round-trips
	 *            have been performed with the peer before the handshake starts).
	 * @param session
	 *            the session to negotiate with the client.
	 * @param recordLayer
	 *            the object to use for sending flights to the peer.
	 * @param sessionListener
	 *            the listener to notify about the session's life-cycle events.
	 * @param config
	 *            the DTLS configuration.
	 * @param maxTransmissionUnit
	 *            the MTU value reported by the network interface the record layer is bound to.
	 * @param taskExecutor
	 *            The executor to use for processing handshake messages.
	 * @throws IllegalStateException
	 *            if the message digest required for computing the FINISHED message hash cannot be instantiated.
	 * @throws IllegalArgumentException
	 *            if the <code>initialMessageSequenceNo</code> is negative.
	 * @throws NullPointerException
	 *            if session, record layer, config or task executor is <code>null</code>.
	 */
	public static ServerHandshaker server(
			final int initialMessageSequenceNo,
			final DTLSSession session,
			final RecordLayer recordLayer,
			final SessionListener sessionListener,
			final DtlsConnectorConfig config,
			final int maxTransmissionUnit,
			final Executor taskExecutor) {

		return new ServerHandshaker(initialMessageSequenceNo, session, recordLayer, sessionListener, config, maxTransmissionUnit, taskExecutor);
	}

	public static ResumingServerHandshaker resumingServer(
			final int sequenceNumber,
			final DTLSSession session,
			final RecordLayer recordLayer,
			final SessionListener sessionListener,
			final DtlsConnectorConfig config,
			final int maxTransmissionUnit,
			final Executor taskExecutor) {

		return new ResumingServerHandshaker(sequenceNumber, session, recordLayer, sessionListener, config, maxTransmissionUnit, taskExecutor);
	}
}

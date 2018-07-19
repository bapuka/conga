/*
 * Copyright 2018 FIX Protocol Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package io.fixprotocol.conga.session.sbe;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;

import io.fixprotocol.conga.sbe.messages.fixp.EstablishDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.EstablishEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.EstablishmentAckDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.EstablishmentAckEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.EstablishmentRejectCode;
import io.fixprotocol.conga.sbe.messages.fixp.EstablishmentRejectDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.EstablishmentRejectEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.MessageHeaderDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.MessageHeaderEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.NegotiateDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.NegotiateEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.NegotiationRejectCode;
import io.fixprotocol.conga.sbe.messages.fixp.NegotiationRejectDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.NegotiationRejectEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.NegotiationResponseDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.NegotiationResponseEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.NotAppliedEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.RetransmissionDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.RetransmissionEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.RetransmitRequestDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.RetransmitRequestEncoder;
import io.fixprotocol.conga.sbe.messages.fixp.SequenceDecoder;
import io.fixprotocol.conga.sbe.messages.fixp.SequenceEncoder;
import io.fixprotocol.conga.session.EstablishmentReject;
import io.fixprotocol.conga.session.FlowType;
import io.fixprotocol.conga.session.NegotiationReject;
import io.fixprotocol.conga.session.SequenceRange;
import io.fixprotocol.conga.session.SessionAttributes;
import io.fixprotocol.conga.session.SessionMessageType;
import io.fixprotocol.conga.session.SessionMessenger;

/**
 * @author Don Mendelson
 *
 */
class SbeSessionMessenger implements SessionMessenger {

  private final UnsafeBuffer directBuffer = new UnsafeBuffer();
  private ByteBuffer establishBuffer;
  private EstablishDecoder establishDecoder;
  private EstablishEncoder establishEncoder;
  private ByteBuffer establishmentAckBuffer;
  private EstablishmentAckDecoder establishmentAckDecoder;
  private EstablishmentAckEncoder establishmentAckEncoder;
  private UnsafeBuffer establishmentAckMutableBuffer;
  private ByteBuffer establishmentRejectBuffer;
  private EstablishmentRejectDecoder establishmentRejectDecoder;
  private EstablishmentRejectEncoder establishmentRejectEncoder;
  private UnsafeBuffer establishmentRejectMutableBuffer;
  private UnsafeBuffer establishMutableBuffer;
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private boolean isClientSession;
  private ByteBuffer negotiateBuffer;
  private NegotiateDecoder negotiateDecoder;
  private NegotiateEncoder negotiateEncoder;
  private UnsafeBuffer negotiateMutableBuffer;
  private ByteBuffer negotiationRejectBuffer;
  private NegotiationRejectDecoder negotiationRejectDecoder;
  private NegotiationRejectEncoder negotiationRejectEncoder;
  private UnsafeBuffer negotiationRejectMutableBuffer;
  private ByteBuffer negotiationResponseBuffer;
  private NegotiationResponseDecoder negotiationResponseDecoder;
  private NegotiationResponseEncoder negotiationResponseEncoder;
  private UnsafeBuffer negotiationResponseMutableBuffer;
  private final ByteBuffer notAppliedBuffer = ByteBuffer.allocateDirect(32);
  private final NotAppliedEncoder notAppliedEncoder = new NotAppliedEncoder();
  private final UnsafeBuffer notAppliedMutableBuffer = new UnsafeBuffer();
  private final ByteBuffer retransmissionBuffer = ByteBuffer.allocateDirect(48);
  private final RetransmissionDecoder retransmissionDecoder = new RetransmissionDecoder();
  private final RetransmissionEncoder retransmissionEncoder = new RetransmissionEncoder();
  private final UnsafeBuffer retransmissionMutableBuffer = new UnsafeBuffer();
  private final ByteBuffer retransmitRequestBuffer = ByteBuffer.allocateDirect(48);
  private final RetransmitRequestDecoder retransmitRequestDecoder = new RetransmitRequestDecoder();
  private final RetransmitRequestEncoder retransmitRequestEncoder = new RetransmitRequestEncoder();
  private final UnsafeBuffer retransmitRequestMutableBuffer = new UnsafeBuffer();
  private final ByteBuffer sequenceBuffer = ByteBuffer.allocateDirect(16);
  private final SequenceDecoder sequenceDecoder = new SequenceDecoder();
  private final SequenceEncoder sequenceEncoder = new SequenceEncoder();
  private final UnsafeBuffer sequenceMutableBuffer = new UnsafeBuffer();

  @Override
  public void decodeEstablishmentAckSessionAttributes(ByteBuffer buffer,
      SessionAttributes sessionAttributes) {
    directBuffer.wrap(buffer);
    headerDecoder.wrap(directBuffer, 0);
    establishmentAckDecoder.wrap(directBuffer, headerDecoder.encodedLength(),
        headerDecoder.blockLength(), headerDecoder.version());

    sessionAttributes.timestamp(establishmentAckDecoder.requestTimestamp());
    sessionAttributes.keepAliveInterval(establishmentAckDecoder.keepaliveInterval());
    sessionAttributes.nextSeqNo(establishmentAckDecoder.nextSeqNo());
  }

  @Override
  public void decodeEstablishSessionAttributes(ByteBuffer buffer,
      SessionAttributes sessionAttributes) {
    directBuffer.wrap(buffer);
    headerDecoder.wrap(directBuffer, 0);
    establishDecoder.wrap(directBuffer, headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());

    sessionAttributes.credentials(new byte[establishDecoder.credentialsLength()]);
    establishDecoder.getCredentials(sessionAttributes.getCredentials(), 0,
        sessionAttributes.getCredentials().length);
    for (int i = 0; i < EstablishDecoder.sessionIdEncodingLength(); i++) {
      sessionAttributes.getSessionId()[i] = (byte) establishDecoder.sessionId(i);
    }
    sessionAttributes.timestamp(establishDecoder.timestamp());
    sessionAttributes.keepAliveInterval(establishDecoder.keepaliveInterval());
    sessionAttributes.nextSeqNo(establishDecoder.nextSeqNo());
  }

  @Override
  public void decodeNegotiateSessionAttributes(ByteBuffer buffer,
      SessionAttributes sessionAttributes) {
    directBuffer.wrap(buffer);
    headerDecoder.wrap(directBuffer, 0);
    negotiateDecoder.wrap(directBuffer, headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());

    sessionAttributes.credentials(new byte[negotiateDecoder.credentialsLength()]);
    negotiateDecoder.getCredentials(sessionAttributes.getCredentials(), 0,
        sessionAttributes.getCredentials().length);
    for (int i = 0; i < NegotiateDecoder.sessionIdEncodingLength(); i++) {
      sessionAttributes.getSessionId()[i] = (byte) negotiateDecoder.sessionId(i);
    }
    sessionAttributes.timestamp(negotiateDecoder.timestamp());
    sessionAttributes.flowType(FlowType.valueOf(negotiateDecoder.clientFlow().name()));
  }

  @Override
  public void decodeNegotiationResponseSessionAttributes(ByteBuffer buffer,
      SessionAttributes sessionAttributes) {
    directBuffer.wrap(buffer);
    headerDecoder.wrap(directBuffer, 0);
    negotiationResponseDecoder.wrap(directBuffer, headerDecoder.encodedLength(),
        headerDecoder.blockLength(), headerDecoder.version());

    sessionAttributes.credentials(new byte[negotiationResponseDecoder.credentialsLength()]);
    negotiationResponseDecoder.getCredentials(sessionAttributes.getCredentials(), 0,
        sessionAttributes.getCredentials().length);
    for (int i = 0; i < NegotiationResponseDecoder.sessionIdEncodingLength(); i++) {
      sessionAttributes.getSessionId()[i] = (byte) negotiationResponseDecoder.sessionId(i);
    }
    sessionAttributes.timestamp(negotiationResponseDecoder.requestTimestamp());
    sessionAttributes.flowType(FlowType.valueOf(negotiationResponseDecoder.serverFlow().name()));
  }

  @Override
  public long decodeSequence(ByteBuffer buffer) {
    directBuffer.wrap(buffer);
    headerDecoder.wrap(directBuffer, 0);
    sequenceDecoder.wrap(directBuffer, headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());
    return sequenceDecoder.nextSeqNo();
  }

  public ByteBuffer encodeEstablish(byte[] sessionId, long timestamp, long heartbeatInterval,
      long nextSeqNo, byte[] credentials) throws IOException, InterruptedException {
    if (!isClientSession()) {
      throw new IllegalStateException("Establish invoked for server session");
    }
    for (int i = 0; i < EstablishEncoder.sessionIdLength(); i++) {
      establishEncoder.sessionId(i, sessionId[i]);
    }
    establishEncoder.timestamp(timestamp).keepaliveInterval(heartbeatInterval).nextSeqNo(nextSeqNo);
    if (credentials != null) {
      establishEncoder.putCredentials(credentials, 0, credentials.length);
    }
    establishBuffer.limit(MessageHeaderEncoder.ENCODED_LENGTH + establishEncoder.encodedLength());
    return establishBuffer.duplicate();
  }

  @Override
  public ByteBuffer encodeEstablishAck(byte[] sessionId, long timestamp, long heartbeatInterval,
      long nextSeqNo) throws IOException, InterruptedException {
    if (isClientSession()) {
      throw new IllegalStateException("Establish Ack invoked for client session");
    }
    for (int i = 0; i < EstablishmentAckEncoder.sessionIdLength(); i++) {
      establishmentAckEncoder.sessionId(i, sessionId[i]);
    }
    establishmentAckEncoder.requestTimestamp(timestamp);
    establishmentAckEncoder.keepaliveInterval(heartbeatInterval);
    establishmentAckEncoder.nextSeqNo(nextSeqNo);
    establishmentAckEncoder
        .limit(MessageHeaderEncoder.ENCODED_LENGTH + establishmentAckEncoder.encodedLength());

    return establishmentAckBuffer.duplicate();
  }

  @Override
  public ByteBuffer encodeEstablishReject(byte[] sessionId, long timestamp,
      EstablishmentReject rejectCode, byte[] reason) throws IOException, InterruptedException {
    if (isClientSession()) {
      throw new IllegalStateException("Establish Reject invoked for client session");
    }
    for (int i = 0; i < EstablishmentRejectEncoder.sessionIdLength(); i++) {
      establishmentRejectEncoder.sessionId(i, sessionId[i]);
    }
    establishmentRejectEncoder.requestTimestamp(timestamp);
    establishmentRejectEncoder.code(EstablishmentRejectCode.valueOf(rejectCode.name()));
    establishmentRejectEncoder.putReason(reason, 0, reason.length);
    establishmentRejectEncoder
        .limit(MessageHeaderEncoder.ENCODED_LENGTH + establishmentRejectEncoder.encodedLength());

    return establishmentRejectBuffer.duplicate();
  }

  @Override
  public ByteBuffer encodeNegotiate(byte[] sessionId, long timestamp, FlowType clientFlow,
      byte[] credentials) throws IOException, InterruptedException {
    if (!isClientSession()) {
      throw new IllegalStateException("Negotiate invoked for server session");
    }
    for (int i = 0; i < NegotiateEncoder.sessionIdLength(); i++) {
      negotiateEncoder.sessionId(i, sessionId[i]);
    }
    negotiateEncoder.timestamp(timestamp);
    negotiateEncoder
        .clientFlow(io.fixprotocol.conga.sbe.messages.fixp.FlowType.valueOf(clientFlow.name()));
    if (credentials != null) {
      negotiateEncoder.putCredentials(credentials, 0, credentials.length);
    }
    negotiateBuffer.limit(MessageHeaderEncoder.ENCODED_LENGTH + negotiateEncoder.encodedLength());
    return negotiateBuffer.duplicate();
  }

  @Override
  public ByteBuffer encodeNegotiationReject(byte[] sessionId, long requestTimestamp,
      NegotiationReject rejectCode, byte[] reason) throws IOException, InterruptedException {
    if (isClientSession()) {
      throw new IllegalStateException("NegotiationReject invoked for client session");
    }
    for (int i = 0; i < NegotiationRejectEncoder.sessionIdLength(); i++) {
      negotiationRejectEncoder.sessionId(i, sessionId[i]);
    }
    negotiationRejectEncoder.requestTimestamp(requestTimestamp);
    negotiationRejectEncoder.code(NegotiationRejectCode.valueOf(rejectCode.name()));
    negotiationRejectEncoder.putReason(reason, 0, reason.length);
    negotiationRejectBuffer
        .limit(MessageHeaderEncoder.ENCODED_LENGTH + negotiationRejectEncoder.encodedLength());

    return negotiationRejectBuffer.duplicate();
  }

  @Override
  public ByteBuffer encodeNegotiationResponse(byte[] sessionId, long requestTimestamp,
      FlowType serverFlow, byte[] credentials) throws IOException, InterruptedException {
    if (isClientSession()) {
      throw new IllegalStateException("NegotiationResponse invoked for client session");
    }
    for (int i = 0; i < NegotiationResponseEncoder.sessionIdLength(); i++) {
      negotiationResponseEncoder.sessionId(i, sessionId[i]);
    }
    negotiationResponseEncoder.requestTimestamp(requestTimestamp);
    negotiationResponseEncoder
        .serverFlow(io.fixprotocol.conga.sbe.messages.fixp.FlowType.valueOf(serverFlow.name()));
    if (credentials != null) {
      negotiationResponseEncoder.putCredentials(credentials, 0, credentials.length);
    }
    negotiationResponseBuffer
        .limit(MessageHeaderEncoder.ENCODED_LENGTH + negotiationResponseEncoder.encodedLength());
    return negotiationResponseBuffer.duplicate();
  }

  public ByteBuffer encodeNotApplied(long fromSeqNo, long count)
      throws IOException, InterruptedException {
    notAppliedEncoder.fromSeqNo(fromSeqNo);
    notAppliedEncoder.count(count);
    return notAppliedBuffer.duplicate();
  }

  @Override
  public ByteBuffer encodeRetransmission(byte[] sessionId, SequenceRange range)
      throws IOException, InterruptedException {
    for (int i = 0; i < RetransmissionEncoder.sessionIdLength(); i++) {
      retransmissionEncoder.sessionId(i, sessionId[i]);
    }
    retransmissionEncoder.requestTimestamp(range.getTimestamp());
    retransmissionEncoder.nextSeqNo(range.getFromSeqNo());
    retransmissionEncoder.count(range.getCount());
    retransmissionBuffer
    .limit(MessageHeaderEncoder.ENCODED_LENGTH + retransmissionEncoder.encodedLength());

    return retransmissionBuffer.duplicate();
  }

  @Override
  public ByteBuffer encodeRetransmitRequest(byte[] sessionId, SequenceRange range)
      throws IOException, InterruptedException {
    for (int i = 0; i < RetransmitRequestEncoder.sessionIdLength(); i++) {
      retransmitRequestEncoder.sessionId(i, sessionId[i]);
    }
    retransmitRequestEncoder.timestamp(range.getTimestamp());
    retransmitRequestEncoder.fromSeqNo(range.getFromSeqNo());
    retransmitRequestEncoder.count(range.getCount());
    retransmitRequestBuffer
    .limit(MessageHeaderEncoder.ENCODED_LENGTH + retransmitRequestEncoder.encodedLength());

    return retransmitRequestBuffer.duplicate();
  }

  @Override
  public ByteBuffer encodeSequence(long nextSeqNo) throws IOException, InterruptedException {
    sequenceEncoder.nextSeqNo(nextSeqNo);
    return sequenceBuffer.duplicate();
  }

  @Override
  public SessionMessageType getMessageType(ByteBuffer buffer) {
    SessionMessageType messageType = SessionMessageType.UNKNOWN;
    directBuffer.wrap(buffer);
    headerDecoder.wrap(directBuffer, 0);
    if (SequenceEncoder.SCHEMA_ID != headerDecoder.schemaId()) {
      messageType = SessionMessageType.APPLICATION;
    } else {
      int templateId = headerDecoder.templateId();
      switch (templateId) {
        case SequenceEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.SEQUENCE;
          break;
        case NotAppliedEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.NOT_APPLIED;
          break;
        case NegotiateEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.NEGOTIATE;
          break;
        case NegotiationResponseEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.NEGOTIATION_RESPONSE;
          break;
        case NegotiationRejectEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.NEGOTIATION_REJECT;
          break;
        case EstablishEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.ESTABLISH;
          break;
        case EstablishmentAckEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.ESTABLISHMENT_ACK;
          break;
        case EstablishmentRejectEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.ESTABLISHMENT_REJECT;
          break;
        case RetransmissionEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.RETRANSMISSION;
          break;
        case RetransmitRequestEncoder.TEMPLATE_ID:
          messageType = SessionMessageType.RETRANSMIT_REQUEST;
          break;
      }
    }

    return messageType;
  }

  @Override
  public void getRetransmissionSequenceRange(ByteBuffer buffer, SequenceRange range) {
    directBuffer.wrap(buffer);
    headerDecoder.wrap(directBuffer, 0);
    retransmissionDecoder.wrap(directBuffer, headerDecoder.encodedLength(),
        headerDecoder.blockLength(), headerDecoder.version());
    range.timestamp(retransmissionDecoder.requestTimestamp())
        .fromSeqNo(retransmissionDecoder.nextSeqNo()).count(retransmissionDecoder.count());
  }

  @Override
  public void getRetransmitRequestSequenceRange(ByteBuffer buffer, SequenceRange range) {
    directBuffer.wrap(buffer);
    headerDecoder.wrap(directBuffer, 0);
    retransmitRequestDecoder.wrap(directBuffer, headerDecoder.encodedLength(),
        headerDecoder.blockLength(), headerDecoder.version());
    range.timestamp(retransmitRequestDecoder.timestamp())
        .fromSeqNo(retransmitRequestDecoder.fromSeqNo()).count(retransmitRequestDecoder.count());
  }

  public void init(boolean isClientSession) {
    this.isClientSession = isClientSession;
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    if (isClientSession()) {
      negotiateBuffer = ByteBuffer.allocateDirect(256);
      negotiateEncoder = new NegotiateEncoder();
      negotiateMutableBuffer = new UnsafeBuffer();
      negotiateMutableBuffer.wrap(negotiateBuffer);
      negotiateEncoder.wrapAndApplyHeader(negotiateMutableBuffer, 0, headerEncoder);

      negotiationResponseDecoder = new NegotiationResponseDecoder();
      negotiationRejectDecoder = new NegotiationRejectDecoder();

      establishBuffer = ByteBuffer.allocateDirect(256);
      establishEncoder = new EstablishEncoder();
      establishMutableBuffer = new UnsafeBuffer();
      establishMutableBuffer.wrap(establishBuffer);
      establishEncoder.wrapAndApplyHeader(establishMutableBuffer, 0, headerEncoder);

      establishmentAckDecoder = new EstablishmentAckDecoder();
      establishmentRejectDecoder = new EstablishmentRejectDecoder();
    } else {
      negotiateDecoder = new NegotiateDecoder();

      negotiationResponseBuffer = ByteBuffer.allocateDirect(256);
      negotiationResponseEncoder = new NegotiationResponseEncoder();
      negotiationResponseMutableBuffer = new UnsafeBuffer();
      negotiationResponseMutableBuffer.wrap(negotiationResponseBuffer);
      negotiationResponseEncoder.wrapAndApplyHeader(negotiationResponseMutableBuffer, 0,
          headerEncoder);

      negotiationRejectBuffer = ByteBuffer.allocateDirect(256);
      negotiationRejectEncoder = new NegotiationRejectEncoder();
      negotiationRejectMutableBuffer = new UnsafeBuffer();
      negotiationRejectMutableBuffer.wrap(negotiationRejectBuffer);
      negotiationRejectEncoder.wrapAndApplyHeader(negotiationRejectMutableBuffer, 0, headerEncoder);

      establishDecoder = new EstablishDecoder();

      establishmentAckBuffer = ByteBuffer.allocateDirect(256);
      establishmentAckEncoder = new EstablishmentAckEncoder();
      establishmentAckMutableBuffer = new UnsafeBuffer();
      establishmentAckMutableBuffer.wrap(establishmentAckBuffer);
      establishmentAckEncoder.wrapAndApplyHeader(establishmentAckMutableBuffer, 0, headerEncoder);

      establishmentRejectBuffer = ByteBuffer.allocateDirect(256);
      establishmentRejectEncoder = new EstablishmentRejectEncoder();
      establishmentRejectMutableBuffer = new UnsafeBuffer();
      establishmentRejectMutableBuffer.wrap(establishmentRejectBuffer);
      establishmentRejectEncoder.wrapAndApplyHeader(establishmentRejectMutableBuffer, 0,
          headerEncoder);
    }
    sequenceMutableBuffer.wrap(sequenceBuffer);
    sequenceEncoder.wrapAndApplyHeader(sequenceMutableBuffer, 0, headerEncoder);
    sequenceBuffer.limit(headerEncoder.encodedLength() + sequenceEncoder.encodedLength());
    notAppliedMutableBuffer.wrap(notAppliedBuffer);
    notAppliedEncoder.wrapAndApplyHeader(notAppliedMutableBuffer, 0, headerEncoder);
    notAppliedBuffer.limit(headerEncoder.encodedLength() + notAppliedEncoder.encodedLength());
    retransmitRequestMutableBuffer.wrap(retransmitRequestBuffer);
    retransmitRequestEncoder.wrapAndApplyHeader(retransmitRequestMutableBuffer, 0, headerEncoder);
    retransmissionMutableBuffer.wrap(retransmissionBuffer);
    retransmissionEncoder.wrapAndApplyHeader(retransmissionMutableBuffer, 0, headerEncoder);

  }

  /**
   * @return
   */
  private boolean isClientSession() {
    return isClientSession;
  }


}

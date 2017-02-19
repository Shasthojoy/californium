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

import static org.junit.Assert.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;

import org.junit.Test;


/**
 * @author hak8fe
 *
 */
public class CertificateVerifyTest {

//	@Test
//	public void test() throws IOException, GeneralSecurityException {
//		PrivateKey key = DtlsTestTools.getClientPrivateKey();
//		System.out.println("key algorithm: " + key.getAlgorithm());
//		Signature sig = Signature.getInstance("SHA384withRSA");
//		System.out.println("Signature algorithm: " + sig.getAlgorithm());
//		sig.initSign(key);
//		sig.update(new byte[]{0x00, 0x01});
//		byte[] signature = sig.sign();
//	}

}

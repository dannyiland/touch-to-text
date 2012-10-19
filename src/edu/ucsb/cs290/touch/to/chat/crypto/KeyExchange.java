package edu.ucsb.cs290.touch.to.chat.crypto;

import java.io.Serializable;
import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.DHParameterSpec;

public class KeyExchange implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private KeyPair keypair;
	public KeyExchange() {
		this(256);
	}
	
	public KeyExchange(int keyBits) {
		try {
		AlgorithmParameterGenerator paramGen = AlgorithmParameterGenerator.getInstance("DH");
        paramGen.init(keyBits);

        // Generate the parameters
        AlgorithmParameters params = paramGen.generateParameters();
        DHParameterSpec dhSpec
            = (DHParameterSpec)params.getParameterSpec(DHParameterSpec.class);

		    // Use the values to generate a key pair
		    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DH");
		    keyGen.initialize(dhSpec);
		    keypair = keyGen.generateKeyPair();
		} catch(Exception e) {
			Logger.getLogger("touch-to-text").log(Level.SEVERE, "Exception in key exchange", e);
		}
	}
	
	public byte[] getPublicKeyBytes() {
	    return keypair.getPublic().getEncoded();
	}
	
	public SecretKey setOtherPublicKey(byte[] b,String algorithm) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
		if(keypair == null) {
			throw new UnsupportedOperationException("Do not reuse key exchange objects.");
		}
	    // Convert the public key bytes into a PublicKey object
	    X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(b);
	    KeyFactory keyFact = KeyFactory.getInstance("DH");

	    // Prepare to generate the secret key with the private key and public key of the other party
	    KeyAgreement ka = KeyAgreement.getInstance("DH");
	    ka.init(keypair.getPrivate());
	    ka.doPhase(keyFact.generatePublic(x509KeySpec), true);
	    // Generate the secret key
	    keypair = null; //Ensure not reused
	    return ka.generateSecret(algorithm);
	}
}
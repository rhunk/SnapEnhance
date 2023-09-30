package me.rhunk.snapenhance.bridge.e2ee;

import me.rhunk.snapenhance.bridge.e2ee.EncryptionResult;

interface E2eeInterface {
    /**
    * Start a new pairing process with a friend
    * @param friendId
    * @return the pairing public key
    */
   @nullable byte[] createKeyExchange(String friendId);

    /**
    * Accept a pairing request from a friend
    * @param friendId
    * @param publicKey the public key received from the friend
    * @return the encapsulated secret to send to the friend
    */
    @nullable byte[] acceptPairingRequest(String friendId, in byte[] publicKey);

    /**
     * Accept a pairing response from a friend
     * @param friendId
     * @param encapsulatedSecret the encapsulated secret received from the friend
     * @return true if the pairing was successful
    */
    boolean acceptPairingResponse(String friendId, in byte[] encapsulatedSecret);

    /**
    * Check if a friend key exists
    * @param friendId
    * @return true if the friend key exists
    */
    boolean friendKeyExists(String friendId);

    /**
    * Get the fingerprint of a secret key
    * @param friendId
    * @return the fingerprint of the secret key
    */
    @nullable String getSecretFingerprint(String friendId);

    @nullable EncryptionResult encryptMessage(String friendId, in byte[] message);

    @nullable byte[] decryptMessage(String friendId, in byte[] message, in byte[] iv);
}
package com.nucypher.kafka.clients.encrypt;

import com.nucypher.kafka.cipher.AesGcmCipher;
import com.nucypher.kafka.clients.MessageHandler;
import com.nucypher.kafka.encrypt.DataEncryptionKeyManager;
import com.nucypher.kafka.errors.CommonException;
import com.nucypher.kafka.utils.EncryptionAlgorithm;
import com.nucypher.kafka.utils.KeyUtils;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Map;

/**
 * The message {@link Serializer} which uses AES and encryption algorithm
 *
 * @param <T> type to be serialized from.
 */
public class AesMessageSerializer<T> implements Serializer<T> {

    /**
     * Configured flag
     */
    protected boolean isConfigured;
    /**
     * Message handler
     */
    protected MessageHandler messageHandler;
    /**
     * Data serializer
     */
    protected Serializer<T> serializer;

    /**
     * Constructor used by Kafka producer
     */
    public AesMessageSerializer() {
        isConfigured = false;
    }

    /**
     * @param serializer Kafka serializer
     * @param algorithm  encryption algorithm
     * @param publicKey  public key
     */
    public AesMessageSerializer(Serializer<T> serializer,
                                EncryptionAlgorithm algorithm,
                                PublicKey publicKey) {
        this.serializer = serializer;
        SecureRandom secureRandom = new SecureRandom();
        DataEncryptionKeyManager keyManager =
                new DataEncryptionKeyManager(algorithm, publicKey, secureRandom);
        AesGcmCipher cipher = new AesGcmCipher();
        messageHandler = new MessageHandler(cipher, keyManager, secureRandom);
        isConfigured = true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        if (!isConfigured) {
            AbstractConfig config = new AesMessageSerializerConfig(configs);
            String path = config.getString(
                    AesMessageSerializerConfig.PUBLIC_KEY_CONFIG);
            PublicKey publicKey;
            try {
                publicKey = KeyUtils.getECKeyPairFromPEM(path).getPublic();
            } catch (IOException e) {
                throw new CommonException(e);
            }
            EncryptionAlgorithm algorithm = EncryptionAlgorithm.valueOf(
                    config.getString(AesMessageSerializerConfig.ALGORITHM_CONFIG));

            SecureRandom secureRandom = new SecureRandom();
            DataEncryptionKeyManager keyManager =
                    new DataEncryptionKeyManager(algorithm, publicKey, secureRandom);
            AesGcmCipher cipher = new AesGcmCipher();
            messageHandler = new MessageHandler(cipher, keyManager, secureRandom);

            if (isKey) {
                serializer = config.getConfiguredInstance(
                        AesMessageSerializerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                        Serializer.class);
            } else {
                serializer = config.getConfiguredInstance(
                        AesMessageSerializerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        Serializer.class);
            }
        }
        serializer.configure(configs, isKey);
        isConfigured = true;
    }

    @Override
    public byte[] serialize(String topic, T data) {
        byte[] bytes = serializer.serialize(topic, data);
        return messageHandler.encrypt(topic, bytes);
    }

    @Override
    public void close() {
        if (serializer != null) {
            serializer.close();
        }
    }
}
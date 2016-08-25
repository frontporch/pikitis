FROM openjdk:8-jre
ENV RELEASE=https://github.com/frontporch/pikitis/releases/download/v1.0/kafka-transform-decrypt-1.0-SNAPSHOT.tar
ENV OUTPUT_DIR=/opt/pikitis

# ENV DECRYPTION_TYPE=adx
# ENV TOPIC_IN="foo-bar-$"
# ENV TOPIC_IN_VALUES="1,2"
# ENV TOPIC_OUT="bar-foo"
# ENV DECRYPTION_KEY=/etc/secrets/fake-decryption.key
ENV KAFKA_BROKERS="192.168.33.11:9092"

RUN cd /tmp && \

    # Get the release
    curl -L -o release.tar ${RELEASE} && \

    # Create working dir
    mkdir -p ${OUTPUT_DIR} && \

    # Extract file
    tar -xvf release.tar -C ${OUTPUT_DIR} && \

    # Clean up after ourselves
    rm -fr /tmp/*

WORKDIR ${OUTPUT_DIR}

CMD ["kafka-transform-decrypt-1.0-SNAPSHOT/bin/kafka-transform-decrypt"]
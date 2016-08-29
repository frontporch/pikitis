FROM openjdk:8-jre
ENV RELEASE=https://github.com/frontporch/pikitis/releases/download/v1.1/kafka-transform-decrypt-v1.1-2c8d9b7.tar
ENV OUTPUT_DIR=/opt/pikitis

# see README.md for other environment variables
ENV KAFKA_BROKERS="192.168.33.11:9092"

RUN cd /tmp && \

    # Get the release
    curl -L -o release.tar ${RELEASE} && \

    # Create working dir
    mkdir -p ${OUTPUT_DIR} && \

    # Extract file
    tar -xvf release.tar -C ${OUTPUT_DIR} && \

    # Move the release folder contents up
    mv ${OUTPUT_DIR}/kafka-transform-decrypt-*/* ${OUTPUT_DIR}

    # Clean up after ourselves
    rm -fr /tmp/*

WORKDIR ${OUTPUT_DIR}

CMD ["bin/kafka-transform-decrypt"]

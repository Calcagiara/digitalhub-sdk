FROM python:3.9

# Repo info
LABEL org.opencontainers.image.source=https://github.com/scc-digitalhub/digitalhub-core

# Set working dir
WORKDIR /digitalhub-core/

# Get digitalhub-sdk
ARG DHUB_VERSION=0.0.11
RUN wget https://github.com/Calcagiara/digitalhub-sdk/archive/refs/tags/$DHUB_VERSION.zip

RUN unzip $DHUB_VERSION.zip
RUN mv digitalhub-sdk-$DHUB_VERSION digitalhub-sdk

# Install digitalhub-core, dbt
RUN pip install ./digitalhub-sdk/core[dbt_yaml]
RUN pip install ./digitalhub-sdk/data/modules/dbt[local]

# Copy wrapper and set entry point
COPY wrapper.py /digitalhub-core/

# Cleanup
RUN rm -rf digitalhub-sdk $DHUB_VERSION.zip

ENTRYPOINT ["python", "wrapper.py"]

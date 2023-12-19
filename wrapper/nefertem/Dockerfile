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

# Install digitalhub-core, neferetem
RUN pip install digitalhub-sdk/core[base_yaml]
RUN pip install digitalhub-sdk/data/modules/nefertem[local]

# Install nefertem plugins
RUN wget https://github.com/scc-digitalhub/nefertem/archive/refs/tags/v2.0.2.zip
RUN unzip v2.0.2.zip
RUN mv nefertem-2.0.2 nefertem
RUN pip install ./nefertem/plugins/frictionless*
RUN pip install ./nefertem/plugins/duckdb*
RUN pip install ./nefertem/plugins/sqlalchemy*
RUN pip install ./nefertem/plugins/ydata*

# Copy wrapper and set entry point
COPY wrapper.py /digitalhub-core/

# Cleanup
RUN rm -rf digitalhub-sdk nefertem $DHUB_VERSION.zip

ENTRYPOINT ["python", "wrapper.py"]

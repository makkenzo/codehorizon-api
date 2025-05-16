FROM python:3.10-slim

WORKDIR /usr/src/app

RUN useradd --create-home --shell /bin/bash appuser
USER appuser
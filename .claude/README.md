# .claude - JordyLab Project Configuration

This directory is located at the root of the JordyLab project at `/JordyLab/.claude` and contains Claude-specific configuration and project documentation.

## Purpose

This directory serves as the central configuration hub for Claude AI assistant interactions with the JordyLab platform.

## Contents

- **CLAUDE.md** - Root project instructions and architecture overview for the entire JordyLab ecosystem
- **coding-master-prompt.md** - Coding conventions and best practices for Java and Angular development
- **jordylab-infrastructure-guide.md** - Infrastructure setup, deployment, and operational documentation
- **jordylab-project-setup.md** - Project scaffolding, dependency management, and build configuration
- **jordylab-project-overview.md** - High-level project goals, monetization strategy, and technical decisions

## About JordyLab

JordyLab is a personal platform for:
- **Financial Intelligence** (fna) - News aggregation, RSS ingestion, AI investment briefings
- **Health/Fitness Tracking** (garmin) - Garmin Connect data synchronization
- **Game Cataloging** (gamecatalog) - ROM/Steam game catalog with semantic search
- **Recipe Management** (recipe) - Self-hosted recipe platform

Built as a modular monolith with:
- **Backend**: Spring Boot 4, Java 25, Gradle Kotlin DSL (`jordylab-be/`)
- **Frontend**: Nx Angular 21, Bun, spartan/ui (`jordylab-fe/`)
- **Sidecar**: Python 3.12 Garmin sync service (`garmin-sync-service/`)

## Infrastructure

- **Hetzner VPS** - Docker Compose production stack
- **Main Desktop** - Ryzen 9 7950X + RX 7900 XTX for Ollama inference (AMD ROCm)
- **JordyBox** - i7-9700K + RTX 2070 Super for HTPC/gaming and NFS storage

## Usage

These files provide context to Claude AI when working on JordyLab components. Refer to `CLAUDE.md` for the master reference and module-specific documentation in each sub-project directory.

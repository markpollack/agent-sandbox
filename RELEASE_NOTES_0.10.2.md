# Agent Sandbox 0.10.2

Corrective maintenance release removing a certification-driven Jackson dependency declaration
from the Docker module. The published dependency graph now follows natural Maven consumer
resolution; the exact consumer-rooted CycloneDX SBOM is certified and published by the shared
AgentWorks release procedure.

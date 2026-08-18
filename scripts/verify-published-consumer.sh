#!/usr/bin/env bash

set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "${script_dir}/.." && pwd)
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/agent-sandbox-consumer-gate.XXXXXX")
isolated_m2="${work_dir}/m2"
consumer_dir="${work_dir}/consumer"

cleanup() {
    rm -rf -- "${work_dir}"
}
trap cleanup EXIT

mkdir -p "${isolated_m2}" "${consumer_dir}"

cd "${repo_root}"
project_version=$(./mvnw --no-transfer-progress -q -N help:evaluate \
    -Dexpression=project.version \
    -DforceStdout)

if [[ -z "${project_version}" ]]; then
    echo "Could not derive the current project version" >&2
    exit 1
fi

echo "Installing Agent Sandbox ${project_version} with release-profile flattening"
./mvnw --no-transfer-progress -B -Prelease clean install \
    -DskipTests \
    -Dgpg.skip=true \
    -Dmaven.repo.local="${isolated_m2}"

flattened_docker_pom="${isolated_m2}/io/github/markpollack/agent-sandbox-docker/${project_version}/agent-sandbox-docker-${project_version}.pom"
if [[ ! -f "${flattened_docker_pom}" ]]; then
    echo "Flattened Docker-module POM was not installed: ${flattened_docker_pom}" >&2
    exit 1
fi
if grep -q '<parent>' "${flattened_docker_pom}"; then
    echo "Installed Docker-module POM is not standalone: it still contains <parent>" >&2
    exit 1
fi

cat > "${consumer_dir}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>verification</groupId>
    <artifactId>agent-sandbox-docker-standalone-consumer</artifactId>
    <version>1.0.0</version>
    <dependencies>
        <dependency>
            <groupId>io.github.markpollack</groupId>
            <artifactId>agent-sandbox-docker</artifactId>
            <version>${project_version}</version>
        </dependency>
    </dependencies>
</project>
EOF

tree_file="${consumer_dir}/dependency-tree.txt"
./mvnw --no-transfer-progress -B -f "${consumer_dir}/pom.xml" \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree \
    -Dscope=runtime \
    -DoutputFile="${tree_file}" \
    -Dmaven.repo.local="${isolated_m2}"

cat "${tree_file}"

resolved_version() {
    local group_id=$1
    local artifact_id=$2
    local versions

    versions=$(sed -n \
        "s/.*${group_id}:${artifact_id}:jar:\([^:]*\):.*/\1/p" \
        "${tree_file}" | sort -u)

    if [[ -z "${versions}" ]]; then
        echo "Required runtime dependency is absent: ${group_id}:${artifact_id}" >&2
        return 1
    fi
    if [[ $(printf '%s\n' "${versions}" | wc -l) -ne 1 ]]; then
        echo "Multiple versions resolved for ${group_id}:${artifact_id}: ${versions}" >&2
        return 1
    fi

    printf '%s' "${versions}"
}

require_floor() {
    local group_id=$1
    local artifact_id=$2
    local minimum=$3
    local actual
    local first

    actual=$(resolved_version "${group_id}" "${artifact_id}")
    first=$(printf '%s\n%s\n' "${minimum}" "${actual}" | sort -V | head -n 1)
    if [[ "${first}" != "${minimum}" ]]; then
        echo "${group_id}:${artifact_id} resolved to ${actual}; required floor is ${minimum}" >&2
        return 1
    fi

    echo "PASS ${group_id}:${artifact_id}=${actual} (minimum ${minimum})"
}

require_floor org.testcontainers testcontainers 1.21.4
require_floor org.apache.commons commons-compress 1.28.0

echo "Standalone published-consumer resolution gate passed for ${project_version}"

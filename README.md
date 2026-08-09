# cdk-mps-plugin

A [DeMAF](https://github.com/UST-DeMAF) plugin that transforms AWS CDK deployment models (the
output of `cdk synth`) into an [EDMM](https://github.com/UST-EDMM) model, using a
[JetBrains MPS](https://www.jetbrains.com/mps/) model-to-model transformation.

The plugin registers with the DeMAF [analysis-manager](https://github.com/UST-DeMAF/analysis-manager)
and runs as part of the full DeMAF stack defined in
[deployment-config](https://github.com/UST-DeMAF/deployment-config).

## Submodule

The MPS project is a separate repository, included here as a submodule:

```shell
git submodule update --init      # after cloning
git submodule update --remote    # update to a newer MPS project version
```

## Run

As part of the DeMAF stack (recommended). The `cdkplugin` service is defined in
[deployment-config](https://github.com/UST-DeMAF/deployment-config); from that repository run:

```shell
docker compose up -d --build
```

Standalone, for development (needs a reachable analysis-manager and models-service):

```shell
./mvnw spring-boot:run
```

## Configuration

Settings are in `src/main/resources/application.properties`:

- `server.port` — plugin port (default `8088`).
- `plugin.technology` — technology name shown in DeMAF (`AWS CDK`).
- `mps.generator.*` — paths into the MPS submodule used by the headless generator.
- `output.debug-dir` — directory where, per transformation, the parser output
  (`cdkDeploymentModel.json`) and the transformation output (`result.yaml`) are written.

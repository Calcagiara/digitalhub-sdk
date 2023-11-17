package it.smartcommunitylabdhub.core.components.kubernetes.kaniko;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

//////////////////////// TO USE THI BUILDER //////////////////////////////
// HelloWorld.java deve essere messo in /target path
//
// FROM {{baseImage}}
//
// # Add additional instructions here
// COPY HelloWorld.java /app
// WORKDIR /app
// RUN javac HelloWorld.java
//
// ENTRYPOINT ["java", "HelloWorld"]
//
//////////////////////////////////////
// DockerBuildConfiguration buildConfig = new DockerBuildConfiguration();
// buildConfig.setBaseImage("adoptopenjdk:11-jdk-hotspot");
// buildConfig.setEntrypointCommand("java HelloWorld");

// try {
//     DockerfileGenerator.generateDockerfile(buildConfig);
//     KanikoImageBuilder.buildDockerImage(buildConfig);
//     String image = "your-registry/your-image-name:tag"; // Specify the image generated by Kaniko
//     KubernetesJobGenerator.generateKubernetesJob(image);
// } catch (IOException e) {
//     e.printStackTrace();
// }
//
///////////////////////////////////////////////////////////////////////////

@Slf4j
public class KanikoImageBuilder {

    // [x]: DONE! this builder work for FOLDER strategy building.
    @Async
    public static CompletableFuture<?> buildDockerImage(
            KubernetesClient kubernetesClient,
            DockerBuildConfig buildConfig,
            JobBuildConfig jobBuildConfig)
            throws IOException {

        // Generate the Dockerfile
        String dockerFileContent = DockerfileGenerator.generateDockerfile(buildConfig);
        String javaFile = Files.readString(
                Path.of("/home/ltrubbiani/Labs/digitalhub-core/kubernetes/target/HelloWorld.java"));
        // Create config map
        ConfigMap configMap = new ConfigMapBuilder()
                .addToData("Dockerfile", dockerFileContent)
                .addToData("HelloWorld.java", javaFile) // Test purpose
                .withNewMetadata()
                .withName("config-map" + jobBuildConfig.getIdentifier())
                .endMetadata()
                .build();

        kubernetesClient.resource(configMap).inNamespace("default").create();

        Secret dockerHubSecret = new SecretBuilder().withNewMetadata()
                .withName("secret" + jobBuildConfig.getIdentifier())
                .endMetadata()
                .withType("kubernetes.io/dockerconfigjson")
                .addToData(".dockerconfigjson", getDockerConfigJson())
                .build();

        kubernetesClient.resource(dockerHubSecret).inNamespace("default").create();

        KeyToPath keyToPath = new KeyToPath();
        keyToPath.setKey(".dockerconfigjson");
        keyToPath.setPath("config.json");

        // Configure Kaniko build
        Job job = new JobBuilder()
                .withNewMetadata()
                .withName("job" + jobBuildConfig.getIdentifier()).endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()

                // COMMENT: Add init container to do all init operations.
                // Add Init container alpine
                .addNewInitContainer()
                .withName("kaniko-init" + jobBuildConfig.getIdentifier())
                .withImage("alpine:latest")
                .withVolumeMounts(
                        new VolumeMountBuilder()
                                .withName("shared-dir")
                                .withMountPath("/shared")
                                .build())
                .withCommand("sh")
                .withArgs("-c", "wget " + buildConfig.getSharedData()
                        + " -O /shared/data.tgz && tar xf /shared/data.tgz -C /shared")
                .endInitContainer()

                // COMMENT: Kaniko container
                // Add Kaniko container
                .addNewContainer()
                .withName("kaniko-container" + jobBuildConfig.getIdentifier())
                .withImage("gcr.io/kaniko-project/executor:latest")
                .withVolumeMounts(
                        new VolumeMountBuilder()
                                .withName("kaniko-config")
                                .withMountPath("/build").build(),
                        new VolumeMountBuilder()
                                .withName("kaniko-secret")
                                .withMountPath("/kaniko/.docker").build(),
                        new VolumeMountBuilder()
                                .withName("shared-dir")
                                .withMountPath("/shared")
                                .build())
                .withEnv(new EnvVarBuilder().withName("DOCKER_CONFIG")
                        .withValue("/kaniko/.docker")
                        .build())

                .withCommand("/kaniko/executor")
                .withArgs("--dockerfile=/build/Dockerfile",
                        "--context=/build",
                        "--destination=ltrubbianifbk/dh" + jobBuildConfig.getIdentifier()
                                + ":latest")
                .endContainer()

                // COMMENT: SHARED VOLUME
                .addNewVolume().withName("shared-dir")
                .endVolume()

                // Kaniko Config
                .addNewVolume().withName("kaniko-config")
                .withNewConfigMap()
                .withName("config-map" + jobBuildConfig.getIdentifier())
                .endConfigMap()
                .endVolume()

                // Kaniko Secret
                .addNewVolume().withName("kaniko-secret")
                .withNewSecret()
                .withSecretName("secret" + jobBuildConfig.getIdentifier())
                .withItems(keyToPath)
                .endSecret()
                .endVolume()

                // Restart Policy
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        // Create the Pod
        kubernetesClient.resource(job).inNamespace("default").create();

        // Wait for the build to complete
        ScalableResource<Job> jobResource = kubernetesClient.batch().v1().jobs()
                .inNamespace("default")
                .withName("job" + jobBuildConfig.getIdentifier());

        // HACK: delay execution to check pod activities
        // try {
        // Thread.sleep(15000); // Adjust the delay as needed
        // } catch (InterruptedException e) {
        // e.printStackTrace();
        // }
        try {
            jobResource.waitUntilCondition(
                    j -> j.getStatus().getSucceeded() != null &&
                            job.getStatus().getSucceeded() > 0,
                    10, TimeUnit.MINUTES);
            log.info("Docker image build completed successfully.");
        } catch (Exception e) {
            log.info("Docker image build failed or timed out: " + e.getMessage());
        }

        // Cleanup the Pod, ConfigMap, and Secret
        jobResource.delete();
        kubernetesClient.configMaps().inNamespace("default")
                .withName("config-map" + jobBuildConfig.getIdentifier()).delete();
        kubernetesClient.secrets().inNamespace("default").withName("secret" + jobBuildConfig.getIdentifier())
                .delete();

        // FIXME: FOR NOW RETURN COMPLETABLE FUTURE OF NULL
        return null;

    }

    /**
     * Kaniko / Docker authentication.
     * Is used to push the image built by kaniko on hub.docker.io
     *
     * @return String
     */
    private static String getDockerConfigJson() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        // Replace with your Docker Hub credentials
        String username = dotenv.get("DOCKER_USERNAME");
        String password = dotenv.get("DOCKER_PASSWORD");
        String email = dotenv.get("DOCKER_EMAIL");

        // Create the Docker config JSON
        Map<String, Object> auths = new HashMap<>();
        Map<String, String> auth = new HashMap<>();
        auth.put("username", username);
        auth.put("password", password);
        auth.put("email", email);
        auth.put("auth", Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));
        auths.put("https://index.docker.io/v1/", auth);

        Map<String, Object> configData = new HashMap<>();
        configData.put("auths", auths);

        ObjectMapper objectMapper = new ObjectMapper();
        String json;
        try {
            json = objectMapper.writeValueAsString(configData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create Docker config JSON.", e);
        }

        // Base64 encode the JSON
        return Base64.getEncoder().encodeToString(json.getBytes());
    }

}

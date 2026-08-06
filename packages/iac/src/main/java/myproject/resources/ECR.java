package myproject.resources;

import com.pulumi.aws.ecr.Repository;
import com.pulumi.aws.ecr.RepositoryArgs;
import com.pulumi.aws.ecr.inputs.GetAuthorizationTokenArgs;
import com.pulumi.aws.ecr.EcrFunctions;
import com.pulumi.docker.Image;
import com.pulumi.docker.ImageArgs;
import com.pulumi.docker.inputs.DockerBuildArgs;
import com.pulumi.docker.inputs.RegistryArgs;

public class ECR {
    public record ECRResult(Repository repository, Image image) {
    }

    public record ECRConfig(String resourceName, String repositoryName, String imageName) {
        public static final ECRConfig DEFAULT = new ECRConfig("users-service-repository", "pragma/users-service",
                "users-service-image");
    }

    public static ECRResult setup() {
        var config = ECRConfig.DEFAULT;

        // Create the ECR repository to store Docker images for the users-service
        Repository repository = new Repository(config.resourceName(),
                RepositoryArgs.builder()
                        .name(config.repositoryName())
                        .imageTagMutability("MUTABLE")
                        .forceDelete(true)
                        .build());

        // Get ECR authorization token to authenticate Docker push
        var authToken = EcrFunctions.getAuthorizationToken(
                GetAuthorizationTokenArgs.builder()
                        .registryId(repository.registryId())
                        .build());

        // Build the Docker image from the users-service Dockerfile and push it to ECR
        Image image = new Image(config.imageName(),
                ImageArgs.builder()
                        .build(DockerBuildArgs.builder()
                                .context("../../apps/users")
                                .dockerfile("../../apps/users/Dockerfile")
                                .platform("linux/amd64") // ECS Fargate runs on x86_64
                                .build())
                        .imageName(repository.repositoryUrl()
                                .applyValue(url -> String.format("%s:latest", url)))
                        .registry(RegistryArgs.builder()
                                .server(repository.repositoryUrl())
                                .username(authToken.applyValue(token -> token.userName()))
                                .password(authToken.applyValue(token -> token.password()))
                                .build())
                        .build());

        return new ECRResult(repository, image);
    }
}

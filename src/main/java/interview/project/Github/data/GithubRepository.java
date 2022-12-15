package interview.project.Github.data;


import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubRepository {
    private String name;
    private String description;
    private String url;
    private boolean fork;

    private GithubOwner owner;
}


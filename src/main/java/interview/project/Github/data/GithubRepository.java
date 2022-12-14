package interview.project.Github.data;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GithubRepository {
    private String name;
    private String description;
    private String url;
    private boolean fork;

    private GithubOwner owner;
}


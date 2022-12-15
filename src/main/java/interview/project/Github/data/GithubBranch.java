package interview.project.Github.data;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GithubBranch {
    private String name;
    private GithubCommit commit;
}


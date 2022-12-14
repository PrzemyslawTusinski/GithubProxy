package interview.project.Github.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ProxyBranch {
    private String name;
    private String lastCommitSha;
}

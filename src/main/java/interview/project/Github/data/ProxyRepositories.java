package interview.project.Github.data;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@EqualsAndHashCode
public class ProxyRepositories {
    private String repositoryName;
    private String ownerLogon;

    private List<ProxyBranch> branches;
}

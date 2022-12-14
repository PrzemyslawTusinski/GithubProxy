package interview.project.Github.data;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProxyResponse {
    private String repositoryName;
    private String ownerLogon;

    private List<ProxyBranch> branches;

    public ProxyResponse(String repositoryName, String ownerLogon) {
        this.repositoryName = repositoryName;
        this.ownerLogon = ownerLogon;
    }
}

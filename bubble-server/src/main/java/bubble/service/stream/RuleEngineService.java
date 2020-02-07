package bubble.service.stream;

public interface RuleEngineService {

    default int flushRuleCache () { return 0; }

}

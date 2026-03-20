package services.sandbox.api;

/**
 * Interface for sandbox portfolio operations: balance, portfolio view, margin info.
 */
public interface ISandboxPortfolioService {

    String register(String userId, String userName);

    String assets();

    String portfolio(String userId);

    String balance(String userId);

    String margin(String userId);

    String price(String ticker);

    String history(String userId);
}

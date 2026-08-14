package app;

import shared.money.Amount;
import souther.runtime.InvariantFailure;
import souther.runtime.Result;

/** Java beside the model, naming it. The processor this plugin replaces gave a project that. */
public final class Purses {

    private Purses() {}

    public static Result<Amount, InvariantFailure> of(long n) {
        return Amount.__construct(n);
    }
}

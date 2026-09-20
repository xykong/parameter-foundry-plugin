package io.jenkins.plugins.unifiedparameters.provider;

import io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix;

/**
 * A governed Provider whose response is a two-axis matrix instead of a flat option list. The
 * selectable values stay the matrix cells, so transports, validation, environment export, and the
 * Pipeline binding are identical to a flat choice.
 */
public interface MatrixProvider {

    ChoiceMatrix loadMatrix(ProviderLoadContext context);
}

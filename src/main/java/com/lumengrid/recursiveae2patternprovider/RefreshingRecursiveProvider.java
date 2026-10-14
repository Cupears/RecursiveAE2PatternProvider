package com.lumengrid.recursiveae2patternprovider;

/** Implemented only by providers that currently contain recursive patterns. */
public interface RefreshingRecursiveProvider {
    boolean recursiveAE2$refreshStalePatterns();
}

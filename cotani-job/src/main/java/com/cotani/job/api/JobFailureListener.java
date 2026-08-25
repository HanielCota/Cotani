package com.cotani.job.api;

/** Receives failed attempts without changing the job outcome. */
@FunctionalInterface
public interface JobFailureListener {
    void onFailure(JobFailure failure);
}

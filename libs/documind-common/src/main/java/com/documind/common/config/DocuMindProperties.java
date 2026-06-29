package com.documind.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared, typed configuration ({@code documind.*}). The Java equivalent of the
 * Python {@code documind_common} settings: retrieval knobs + where uploaded
 * files live. Provider/model selection itself is handled by Spring AI's
 * {@code spring.ai.*} properties in each service (see their application.yml).
 */
@ConfigurationProperties(prefix = "documind")
public class DocuMindProperties {

    private final Retrieval retrieval = new Retrieval();

    /** Where document-service writes uploaded PDFs (a shared volume in compose). */
    private String storageDir = "/data/storage";

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    /** Hybrid-retrieval tuning. */
    public static class Retrieval {
        /** Final number of chunks fed to the prompt. */
        private int topK = 4;
        /** Candidates pulled from EACH arm (vector, keyword) before fusion. */
        private int candidates = 20;
        /** When false, run vector-only (keyword arm + RRF skipped). */
        private boolean hybridEnabled = true;
        /** Max chunks read in whole-document mode (list-all / summarize). */
        private int wholeDocumentLimit = 120;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getCandidates() {
            return candidates;
        }

        public void setCandidates(int candidates) {
            this.candidates = candidates;
        }

        public boolean isHybridEnabled() {
            return hybridEnabled;
        }

        public void setHybridEnabled(boolean hybridEnabled) {
            this.hybridEnabled = hybridEnabled;
        }

        public int getWholeDocumentLimit() {
            return wholeDocumentLimit;
        }

        public void setWholeDocumentLimit(int wholeDocumentLimit) {
            this.wholeDocumentLimit = wholeDocumentLimit;
        }
    }
}

package com.github.cstroe.svndumpgui.internal.transform;

import com.github.cstroe.svndumpgui.api.SvnDumpConsumer;
import com.github.cstroe.svndumpgui.api.SvnDumpError;
import com.github.cstroe.svndumpgui.api.SvnDumpMutator;
import com.github.cstroe.svndumpgui.api.SvnDumpPreamble;
import com.github.cstroe.svndumpgui.api.SvnDumpValidator;
import com.github.cstroe.svndumpgui.api.SvnDumpWriter;
import com.github.cstroe.svndumpgui.api.SvnRevision;
import com.github.cstroe.svndumpgui.internal.SvnDumpErrorAggregator;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConsumerChain implements SvnDumpMutator, SvnDumpValidator, SvnDumpWriter {

    private List<SvnDumpMutator>   mutators   = new ArrayList<>();
    private List<SvnDumpValidator> validators = new ArrayList<>();
    private List<SvnDumpWriter>    writers    = new ArrayList<>();

    public void add(SvnDumpMutator mutator) {
        mutators.add(mutator);
    }

    public void add(SvnDumpValidator validator) {
        validators.add(validator);
    }

    public void add(SvnDumpWriter writer) {
        writers.add(writer);
    }

    @Override
    public void consume(SvnDumpPreamble preamble) {
          mutators.stream().forEach(c -> c.consume(preamble));
        validators.stream().forEach(c -> c.consume(preamble));
           writers.stream().forEach(c -> c.consume(preamble));
    }

    @Override
    public void consume(SvnRevision revision) {
          mutators.stream().forEach(c -> c.consume(revision));
        validators.stream().forEach(c -> c.consume(revision));
           writers.stream().forEach(c -> c.consume(revision));
    }

    @Override
    public void finish() {
          mutators.stream().forEach(SvnDumpConsumer::finish);
        validators.stream().forEach(SvnDumpConsumer::finish);
           writers.stream().forEach(SvnDumpConsumer::finish);
    }

    @Override
    public boolean isValid() {
        Optional<SvnDumpValidator> failingValidator = validators.stream()
            .filter(v -> !v.isValid()).findFirst();
        return !failingValidator.isPresent();
    }

    @Override
    public SvnDumpError getError() {
        SvnDumpErrorAggregator allErrors = new SvnDumpErrorAggregator();
        validators.stream().map(SvnDumpValidator::getError).forEach(allErrors::add);
        return allErrors;
    }

    @Override
    public void writeTo(OutputStream os) {
        writers.stream().forEach(w -> writeTo(os));
    }
}
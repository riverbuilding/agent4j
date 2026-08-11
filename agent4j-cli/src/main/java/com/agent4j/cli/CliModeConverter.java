package com.agent4j.cli;

import picocli.CommandLine.ITypeConverter;

public final class CliModeConverter implements ITypeConverter<CliMode> {
    @Override
    public CliMode convert(String value) {
        return CliMode.fromWireName(value);
    }
}

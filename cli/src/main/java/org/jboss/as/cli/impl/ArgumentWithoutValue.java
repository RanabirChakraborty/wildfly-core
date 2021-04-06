/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl;

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.common.Assert.checkNotEmptyParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.operation.ParsedCommandLine;


/**
 *
 * @author Alexey Loubyansky
 */
public class ArgumentWithoutValue implements CommandArgument {

    protected final int index;
    protected final String fullName;
    protected final String shortName;

    protected List<CommandArgument> requiredPreceding;
    protected List<CommandArgument> cantAppearAfter = Collections.emptyList();
    protected boolean exclusive;

    protected AccessRequirement access = AccessRequirement.NONE;

    public ArgumentWithoutValue(CommandHandlerWithArguments handler, String fullName) {
        this(handler, -1, fullName);
    }

    public ArgumentWithoutValue(CommandHandlerWithArguments handler, String fullName, String shortName) {
        checkNotNullParam("handler", handler);
        this.fullName = checkNotEmptyParam("fullName", fullName);
        this.shortName = shortName;
        this.index = -1;

        handler.addArgument(this);
    }

    public ArgumentWithoutValue(CommandHandlerWithArguments handler, int index, String fullName) {
        checkNotNullParam("handler", handler);
        this.fullName = checkNotEmptyParam("fullName", fullName);
        this.shortName = null;
        this.index = index;

        handler.addArgument(this);
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void addRequiredPreceding(CommandArgument arg) {
        checkNotNullParam("arg", arg);
        if(requiredPreceding == null) {
            requiredPreceding = Collections.singletonList(arg);
            return;
        }
        if(requiredPreceding.size() == 1) {
            requiredPreceding = new ArrayList<CommandArgument>(requiredPreceding);
        }
        requiredPreceding.add(arg);
    }

    public void addCantAppearAfter(CommandArgument arg) {
        if(cantAppearAfter.isEmpty()) {
            cantAppearAfter = new ArrayList<CommandArgument>();
        }
        cantAppearAfter.add(arg);
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public CommandLineCompleter getValueCompleter() {
        return null;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandArgument#getValue(org.jboss.as.cli.CommandContext)
     */
    @Override
    public String getValue(ParsedCommandLine args) {
        try {
            return getValue(args, false);
        } catch (CommandFormatException e) {
            return null;
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandArgument#getValue(org.jboss.as.cli.CommandContext)
     */
    @Override
    public String getValue(ParsedCommandLine args, boolean required) throws CommandFormatException {
        if(!required) {
            return null;
        }
        if(isPresent(args)) {
            return null;
        }
        throw new CommandFormatException("Required argument '" + fullName + "' is missing value.");
    }

    @Override
    public boolean isPresent(ParsedCommandLine args) throws CommandFormatException {
        if(!args.hasProperties()) {
            return false;
        }

        if (index >= 0 && index < args.getOtherProperties().size()) {
            return true;
        }

        if(args.hasProperty(fullName)) {
            return true;
        }

        if(shortName != null && args.hasProperty(shortName)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isValueComplete(ParsedCommandLine args) throws CommandFormatException {

/*        if (index >= 0 && index < args.getOtherProperties().size()) {
            return true;
        }
*/
        if(args.hasProperty(fullName)) {
            return true;
        }

        return shortName != null && args.hasProperty(shortName);
    }

    @Override
    public String getFullName() {
        return fullName;
    }

    @Override
    public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {

        if(!access.isSatisfied(ctx)) {
            return false;
        }

        ParsedCommandLine args = ctx.getParsedCommandLine();
        if (exclusive) {
            final Set<String> propertyNames = args.getPropertyNames();
            if(propertyNames.isEmpty()) {
                final List<String> values = args.getOtherProperties();
                if(values.isEmpty()) {
                    return true;
                }
                if(index == -1) {
                    return false;
                }
                return !(index == 0 && values.size() == 1);
            }

            if(propertyNames.size() != 1) {
                return false;
            }

            if(args.getLastParsedPropertyName() == null) {
                return false;
            }

            final List<String> values = args.getOtherProperties();
            if(!values.isEmpty()) {
                return false;
            }

            // The argument is already there, don't add it.
            if (fullName.equals(args.getLastParsedPropertyName())) {
                return false;
            }

            return fullName.startsWith(args.getLastParsedPropertyName()) || (shortName != null && shortName.startsWith(args.getLastParsedPropertyName()));
        }

        if (isPresent(args)) {
            // An argument without value has no value
            return false;
        }

        for (CommandArgument arg : cantAppearAfter) {
            if (arg.isPresent(args)) {
                return false;
            }
        }

        if (requiredPreceding != null) {
            for (CommandArgument arg : requiredPreceding) {
                if (arg.isPresent(args)) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    @Override
    public boolean isValueRequired() {
        return false;
    }

    @Override
    public String getShortName() {
        return shortName;
    }

    public void setAccessRequirement(AccessRequirement access) {
        this.access = checkNotNullParam("access", access);
    }
}

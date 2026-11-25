package org.codeberg.edryal.ledger.bookkeeping.application;

import java.util.List;
import java.util.Optional;

/** One page of a statement, newest first. {@code next} is empty on the last page. */
public record StatementPage(List<StatementLine> lines, Optional<Cursor> next) {
}

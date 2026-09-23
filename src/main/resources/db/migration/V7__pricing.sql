-- A theater's default price per seat category, copied onto each show when it's created.
CREATE TABLE theater_category_price (
    theater_id   BIGINT  NOT NULL,
    category_id  BIGINT  NOT NULL,
    price_paise  BIGINT  NOT NULL CHECK (price_paise > 0),
    PRIMARY KEY (theater_id, category_id),
    CONSTRAINT theater_category_price_theater_fk FOREIGN KEY (theater_id) REFERENCES theater(id),
    CONSTRAINT theater_category_price_category_fk FOREIGN KEY (category_id) REFERENCES seat_category(id)
);

-- The show's own copy, so changing a theater's defaults never changes shows already scheduled.
CREATE TABLE show_category_price (
    show_id      BIGINT   NOT NULL REFERENCES show(id),
    category_id  BIGINT   NOT NULL REFERENCES seat_category(id),
    price_paise  BIGINT   NOT NULL CHECK (price_paise > 0),
    overridden   BOOLEAN  NOT NULL DEFAULT FALSE,   -- TRUE when the admin set it for this show
    PRIMARY KEY (show_id, category_id)
);

-- Lowest category price, shown on the browse page ("from ₹300").
ALTER TABLE show ADD COLUMN price_from_paise BIGINT;

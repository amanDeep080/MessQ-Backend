ALTER TABLE menu_items ADD COLUMN dietary_tag VARCHAR(20) NOT NULL DEFAULT 'VEGETARIAN';
UPDATE menu_items SET dietary_tag='NONVEG' WHERE vegetarian=FALSE;

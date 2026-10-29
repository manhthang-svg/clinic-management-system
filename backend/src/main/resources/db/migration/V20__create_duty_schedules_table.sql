CREATE TABLE duty_schedules (
    id UUID NOT NULL,
    work_date DATE NOT NULL,
    shift VARCHAR(20) NOT NULL,
    staff_account_id UUID NOT NULL,
    room_id UUID,
    notes VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    assigned_by_account_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_duty_schedules PRIMARY KEY (id),
    CONSTRAINT fk_duty_schedules_staff FOREIGN KEY (staff_account_id) REFERENCES staff_accounts(id),
    CONSTRAINT fk_duty_schedules_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE SET NULL,
    CONSTRAINT ck_duty_schedules_shift CHECK (shift IN ('MORNING', 'AFTERNOON')),
    CONSTRAINT uq_duty_schedules_staff_date_shift UNIQUE (work_date, shift, staff_account_id)
);

CREATE INDEX idx_duty_schedules_date ON duty_schedules (work_date);
CREATE INDEX idx_duty_schedules_room ON duty_schedules (room_id);
CREATE INDEX idx_duty_schedules_staff ON duty_schedules (staff_account_id);

ALTER TABLE paraclinical_order_details
    ADD COLUMN assigned_technician_account_id UUID REFERENCES staff_accounts(id);

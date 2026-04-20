-- Add missing indices required by spec (Complexity Targets section)

CREATE INDEX idx_member_subscription_end_date ON member_subscription (end_date);
CREATE INDEX idx_session_session_type ON session (session_type);

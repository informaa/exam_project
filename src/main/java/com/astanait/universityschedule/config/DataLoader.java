package com.astanait.universityschedule.config;

import com.astanait.universityschedule.model.Role;
import com.astanait.universityschedule.model.User;
import com.astanait.universityschedule.repository.RoleRepository;
import com.astanait.universityschedule.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

public class DataLoader {
}

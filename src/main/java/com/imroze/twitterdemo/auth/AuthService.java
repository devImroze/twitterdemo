package com.imroze.twitterdemo.auth;

import com.imroze.twitterdemo.exceptions.TwitterDemoClientException;
import com.imroze.twitterdemo.exceptions.TwitterDemoNotFoundException;
import com.imroze.twitterdemo.exceptions.TwitterDemoUnauthorizedException;
import com.imroze.twitterdemo.auth.data.RegistrationType;
import com.imroze.twitterdemo.auth.data.Role;
import com.imroze.twitterdemo.auth.data.SessionStatus;
import com.imroze.twitterdemo.auth.data.UserData;
import com.imroze.twitterdemo.auth.data.request.LoginRequest;
import com.imroze.twitterdemo.auth.data.request.UserRegistrationData;
import com.imroze.twitterdemo.auth.data.response.LoginResponse;
import com.imroze.twitterdemo.utility.JWTUtil;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

  @Autowired private BCryptPasswordEncoder bCryptPasswordEncoder;

  @Autowired private UserDataRepository userDataRepository;

  @Autowired private JWTUtil jwtUtil;

  public Mono<LoginResponse> registerUser(UserRegistrationData userRegistrationData) {

    if (!userRegistrationData.getUserName().matches("^[a-zA-Z0-9]{4,20}$")) {
      return Mono.error(
          new TwitterDemoClientException(
              new RuntimeException(), "User shall not contains any special characters"));
    }

    return Mono.zip(
            userDataRepository
                .existsById(userRegistrationData.getUserName())
                .filter(aBoolean -> !aBoolean)
                .switchIfEmpty(
                    Mono.error(() -> new TwitterDemoClientException("UserName Already Exist"))),
            userDataRepository
                .existsUserDataByEmail(userRegistrationData.getEmail())
                .filter(aBoolean -> !aBoolean)
                .switchIfEmpty(
                    Mono.error(() -> new TwitterDemoClientException("Email Already Exist"))),
            userDataRepository
                .existsUserDataByNumber(userRegistrationData.getNumber())
                .filter(aBoolean -> !aBoolean)
                .switchIfEmpty(
                    Mono.error(() -> new TwitterDemoClientException("Phone number Already Exist"))))
        .flatMap(
            objects -> {
              UserData userData = new UserData();
              userData.setUserName(userRegistrationData.getUserName());
              userData.setEmail(userRegistrationData.getEmail());
              userData.setNumber(userRegistrationData.getNumber());
              userData.setName(userRegistrationData.getName());
              userData.setRecoveryEmail(userRegistrationData.getRecoveryEmail());
              userData.setProfilePictureUrl(userRegistrationData.getProfilePictureUrl());
              userData.setPassword(
                  bCryptPasswordEncoder.encode(userRegistrationData.getPassword()));
              userData.setSessionStatus(
                  userRegistrationData.getRegistrationType() == RegistrationType.LOGIN
                      ? SessionStatus.ACTIVE
                      : SessionStatus.INACTIVE);

              return userDataRepository
                  .save(userData)
                  .map(
                      userData1 ->
                          LoginResponse.builder()
                              .token(
                                  userRegistrationData.getRegistrationType()
                                          == RegistrationType.LOGIN
                                      ? jwtUtil.generateToken(
                                          Role.ROLE_USER,
                                          userData.getUserName(),
                                          userData.getEmail(),
                                          userData.getNumber())
                                      : null)
                              .build());
            });
  }

  public Mono<LoginResponse> login(LoginRequest loginRequest) {

    switch (loginRequest.getLoginType()) {
      case USERNAME:
        return userDataRepository
            .findById(loginRequest.getUser())
            .switchIfEmpty(
                Mono.error(
                    () ->
                        new TwitterDemoNotFoundException(
                            new RuntimeException(), "Username doesn't exist!")))
            .filter(
                userData ->
                    bCryptPasswordEncoder.matches(
                        loginRequest.getPassword(), userData.getPassword()))
            .switchIfEmpty(
                Mono.error(
                    () ->
                        new TwitterDemoUnauthorizedException(
                            new RuntimeException(), "Invalid Password!")))
            .flatMap(
                userData -> {
                  userData.setSessionStatus(SessionStatus.ACTIVE);
                  return userDataRepository.save(userData);
                })
            .map(
                userData ->
                    LoginResponse.builder()
                        .token(
                            jwtUtil.generateToken(
                                Role.ROLE_USER,
                                userData.getUserName(),
                                userData.getEmail(),
                                userData.getNumber()))
                        .build());
      case EMAIL:
        return userDataRepository
            .findUserDataByEmail(loginRequest.getUser())
            .switchIfEmpty(
                Mono.error(
                    () ->
                        new TwitterDemoNotFoundException(
                            new RuntimeException(), "Email doesn't exist!")))
            .filter(
                userData ->
                    bCryptPasswordEncoder.matches(
                        loginRequest.getPassword(), userData.getPassword()))
            .switchIfEmpty(
                Mono.error(
                    () ->
                        new TwitterDemoUnauthorizedException(
                            new RuntimeException(), "Invalid Password!")))
            .flatMap(
                userData -> {
                  userData.setSessionStatus(SessionStatus.ACTIVE);
                  return userDataRepository.save(userData);
                })
            .map(
                userData ->
                    LoginResponse.builder()
                        .token(
                            jwtUtil.generateToken(
                                Role.ROLE_USER,
                                userData.getUserName(),
                                userData.getEmail(),
                                userData.getNumber()))
                        .build());
      case NUMBER:
        return userDataRepository
            .findUserDataByNumber(loginRequest.getUser())
            .switchIfEmpty(
                Mono.error(
                    () ->
                        new TwitterDemoNotFoundException(
                            new RuntimeException(), "Number doesn't exist!")))
            .filter(
                userData ->
                    bCryptPasswordEncoder.matches(
                        loginRequest.getPassword(), userData.getPassword()))
            .switchIfEmpty(
                Mono.error(
                    () ->
                        new TwitterDemoUnauthorizedException(
                            new RuntimeException(), "Invalid Password!")))
            .flatMap(
                userData -> {
                  userData.setSessionStatus(SessionStatus.ACTIVE);
                  return userDataRepository.save(userData);
                })
            .map(
                userData ->
                    LoginResponse.builder()
                        .token(
                            jwtUtil.generateToken(
                                Role.ROLE_USER,
                                userData.getUserName(),
                                userData.getEmail(),
                                userData.getNumber()))
                        .build());
      default:
        return Mono.error(
            () -> new TwitterDemoClientException(new RuntimeException(), "Invalid Data"));
    }
  }

  public Mono<String> logoutUser(HashMap<String, String> username) {
    return userDataRepository
        .findById(username.get("userName"))
        .switchIfEmpty(
            Mono.error(
                () -> new TwitterDemoNotFoundException(new RuntimeException(), "User not found!")))
        .flatMap(
            userData -> {
              userData.setSessionStatus(SessionStatus.INACTIVE);
              return userDataRepository.save(userData).map(userData1 -> "User logged out!");
            });
  }
}

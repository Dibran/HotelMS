package net.mklew.hotelms.inhouse.web.rest;

import com.google.common.base.Optional;
import com.sun.jersey.spi.resource.Singleton;
import net.mklew.hotelms.domain.booking.GuestRepository;
import net.mklew.hotelms.domain.guests.Guest;
import net.mklew.hotelms.inhouse.web.dto.ErrorDto;
import net.mklew.hotelms.inhouse.web.dto.GuestDto;
import net.mklew.hotelms.inhouse.web.dto.MissingGuestInformation;
import net.mklew.hotelms.persistance.hibernate.configuration.HibernateSessionFactory;
import org.hibernate.Session;
import org.jcontainer.dna.Logger;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Marek Lewandowski <marek.m.lewandowski@gmail.com>
 * @since 11/30/12
 *        time 7:44 PM
 */
@Singleton
@Path("guests")
public class GuestResource
{
    private final Logger logger;
    private final HibernateSessionFactory hibernateSessionFactory;
    private final GuestRepository guestRepository;

    public GuestResource(Logger logger, HibernateSessionFactory hibernateSessionFactory,
                         GuestRepository guestRepository)
    {
        this.logger = logger;
        this.hibernateSessionFactory = hibernateSessionFactory;
        this.guestRepository = guestRepository;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<GuestDto> byCommonName(@QueryParam("q") String query)
    {
        if (query == null)
        {
            return Collections.emptyList();
        }
        final Session session = hibernateSessionFactory.getCurrentSession();
        session.beginTransaction();

        String commonName = query;

        // split on space and get firstname and possibly surname
        final String[] split = commonName.split(" ");
        final String firstName = split[0];
        String surname = "";
        if (split.length > 1)
        {
            surname = split[1];
        }

        final Collection<Guest> guests = guestRepository.findAllWhereNameLike(firstName, surname);
        final Collection<GuestDto> guestDtos = GuestDto.fromGuests(guests);

        session.getTransaction().commit();
        return guestDtos;
    }

    @GET
    public Response getAllGuests()
    {
        final Session session = hibernateSessionFactory.getCurrentSession();
        session.beginTransaction();
        final Collection<Guest> guests = guestRepository.findAll();
        final Collection<GuestDto> guestDtos = GuestDto.fromGuests(guests);
        session.getTransaction().commit();
        return Response.ok(guestDtos, MediaType.APPLICATION_JSON_TYPE).status(HttpServletResponse.SC_OK).build();
    }

    @GET
    @Path("/inhouse")
    public Response getAllInHouseGuests()
    {
        final Session session = hibernateSessionFactory.getCurrentSession();
        session.beginTransaction();
        final Collection<Guest> guests = guestRepository.findAllInHouse();
        final Collection<GuestDto> guestDtos = GuestDto.fromGuests(guests);
        session.getTransaction().commit();
        return Response.ok(guestDtos, MediaType.APPLICATION_JSON_TYPE).status(HttpServletResponse.SC_OK).build();
    }

    @GET
    @Path("/guest/{id}")
    public Response getGuestById(@PathParam("id") String id)
    {
        final Session session = hibernateSessionFactory.getCurrentSession();
        session.beginTransaction();
        final Optional<Guest> guestOptional = guestRepository.lookup(Long.valueOf(id));
        if (guestOptional.isPresent())
        {
            final Guest guest = guestOptional.get();
            final GuestDto guestDto = GuestDto.fromGuest(guest);
            session.getTransaction().commit();
            return Response.ok(guestDto, MediaType.APPLICATION_JSON_TYPE).status(HttpServletResponse.SC_OK).build();
        }
        else
        {
            session.getTransaction().commit();
            return Response.ok(new ErrorDto("Guest with id " + id + " has not been found", "GUEST-NOT-FOUND"),
                    MediaType.APPLICATION_JSON_TYPE).status(HttpServletResponse.SC_NOT_FOUND).build();
        }
    }

    @POST
    @Path("/guest/")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createNewGuest(MultivaluedMap<String, String> formParams)
    {
        Session session = hibernateSessionFactory.getCurrentSession();
        session.beginTransaction();
        logger.debug("Got new reservation with parameters: " + formParams.toString());
        try
        {
            GuestDto guestDto = GuestDto.fromNewGuestForm(formParams);
            Guest guest = new Guest(guestDto.socialTitle, guestDto.firstName,
                    guestDto.surname, guestDto.gender, guestDto.idType,
                    guestDto.idNumber, guestDto.phoneNumber);
            guest.setPreferences(guestDto.preferences);
            guest.setDateOfBirth(guestDto.dateOfBirthDate);
            guestRepository.saveGuest(guest);
            GuestDto created = GuestDto.fromGuest(guest);
            session.getTransaction().commit();
            return Response.ok(created, MediaType.APPLICATION_JSON_TYPE).status(HttpServletResponse.SC_CREATED).build();
        }
        catch (MissingGuestInformation missingGuestInformation)
        {
            session.getTransaction().rollback();
            return Response.ok(new ErrorDto("Missing guest information", "GUEST-MISSING-INFO"),
                    MediaType.APPLICATION_JSON_TYPE).status
                    (Response.Status.BAD_REQUEST).build();
        }
    }
}
